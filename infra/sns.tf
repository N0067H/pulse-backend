resource "aws_sns_topic" "pulse_alert" {
  name = "pulse-alert"
}

resource "aws_sns_topic_subscription" "pulse_alert_email" {
  topic_arn = aws_sns_topic.pulse_alert.arn
  protocol  = "email"
  endpoint  = var.alert_email
}
