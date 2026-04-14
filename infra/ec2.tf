resource "aws_iam_role" "pulse_ec2_role" {
  name = "pulse-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "pulse_dynamodb_policy" {
  name = "pulse-dynamodb-policy"
  role = aws_iam_role.pulse_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "dynamodb:GetItem",
        "dynamodb:PutItem",
        "dynamodb:UpdateItem",
        "dynamodb:DeleteItem",
        "dynamodb:Scan",
        "dynamodb:Query"
      ]
      Resource = [
        "arn:aws:dynamodb:ap-northeast-2:${var.aws_account_id}:table/apis",
        "arn:aws:dynamodb:ap-northeast-2:${var.aws_account_id}:table/check_results"
      ]
    }]
  })
}

# SNS 권한
resource "aws_iam_role_policy" "pulse_sns_policy" {
  name = "pulse-sns-policy"
  role = aws_iam_role.pulse_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sns:Publish"]
      Resource = aws_sns_topic.pulse_alert.arn
    }]
  })
}

resource "aws_iam_instance_profile" "pulse_ec2_profile" {
  name = "pulse-ec2-profile"
  role = aws_iam_role.pulse_ec2_role.name
}

resource "aws_instance" "pulse" {
  ami                  = "ami-0c9c942bd7bf113a2" # Amazon Linux 2023, ap-northeast-2
  instance_type        = "t3.micro"
  iam_instance_profile = aws_iam_instance_profile.pulse_ec2_profile.name

  tags = {
    Name = "pulse"
  }
}

output "pulse_ec2" {
  value = {
    public_ip  = aws_instance.pulse.public_ip
    public_dns = aws_instance.pulse.public_dns
    instance_id = aws_instance.pulse.id
  }
}