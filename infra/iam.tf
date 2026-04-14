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
        "arn:aws:dynamodb:${var.aws_region}:${data.aws_caller_identity.current.account_id}:table/apis",
        "arn:aws:dynamodb:${var.aws_region}:${data.aws_caller_identity.current.account_id}:table/check_results"
      ]
    }]
  })
}

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
