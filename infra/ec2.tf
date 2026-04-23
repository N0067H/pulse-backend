resource "aws_instance" "pulse" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = "t3.micro"
  iam_instance_profile   = aws_iam_instance_profile.pulse_ec2_profile.name
  vpc_security_group_ids = [aws_security_group.pulse_ec2.id]

  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail

    dnf install -y java-21-amazon-corretto-headless
    systemctl enable amazon-ssm-agent && systemctl start amazon-ssm-agent

    useradd -r -s /sbin/nologin pulse
    mkdir -p /opt/pulse /var/log/pulse
    chown pulse:pulse /opt/pulse /var/log/pulse

    cat > /opt/pulse/deploy.sh <<'DEPLOY'
    #!/bin/bash
    set -euo pipefail
    aws s3 cp s3://${aws_s3_bucket.artifacts.bucket}/pulse-backend.jar /opt/pulse/pulse-backend.jar
    chown pulse:pulse /opt/pulse/pulse-backend.jar
    systemctl restart pulse
    DEPLOY
    chmod +x /opt/pulse/deploy.sh

    cat > /etc/systemd/system/pulse.service <<'SERVICE'
    [Unit]
    Description=Pulse Backend
    After=network.target

    [Service]
    User=pulse
    WorkingDirectory=/opt/pulse
    ExecStart=/usr/bin/java -jar /opt/pulse/pulse-backend.jar --spring.profiles.active=production
    Restart=on-failure
    RestartSec=5
    StandardOutput=append:/var/log/pulse/app.log
    StandardError=append:/var/log/pulse/app.log
    Environment=AWS_REGION=${var.aws_region}
    Environment=AWS_SNS_TOPIC_ARN=${aws_sns_topic.pulse_alert.arn}

    [Install]
    WantedBy=multi-user.target
    SERVICE

    systemctl daemon-reload
    systemctl enable pulse
  EOF

  user_data_replace_on_change = true

  tags = {
    Name = "pulse"
  }
}
