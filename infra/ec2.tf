resource "aws_instance" "pulse" {
  ami                  = data.aws_ami.amazon_linux_2023.id
  instance_type        = "t3.micro"
  iam_instance_profile = aws_iam_instance_profile.pulse_ec2_profile.name

  tags = {
    Name = "pulse"
  }
}
