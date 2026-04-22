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

resource "aws_iam_role_policy" "pulse_cloudwatch_policy" {
  name = "pulse-cloudwatch-policy"
  role = aws_iam_role.pulse_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["cloudwatch:PutMetricData"]
      Resource = "*"
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

resource "aws_iam_role_policy" "pulse_s3_read_policy" {
  name = "pulse-s3-read-policy"
  role = aws_iam_role.pulse_ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = "${aws_s3_bucket.artifacts.arn}/pulse-backend.jar"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "pulse_ssm_core" {
  role       = aws_iam_role.pulse_ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "pulse_ec2_profile" {
  name = "pulse-ec2-profile"
  role = aws_iam_role.pulse_ec2_role.name
}
