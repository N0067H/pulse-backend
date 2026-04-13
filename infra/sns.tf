variable "alert_email" {
  type        = string
  description = "SNS alert email"
}

resource "aws_sns_topic" "pulse_alert" {
  name = "pulse-alert"
}

resource "aws_sns_topic_subscription" "pulse_alert_email" {
  topic_arn = aws_sns_topic.pulse_alert.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

output "sns" {
  value = {
    pulse_alert = {
      name      = aws_sns_topic.pulse_alert.name
      arn       = aws_sns_topic.pulse_alert.arn
      protocol  = aws_sns_topic_subscription.pulse_alert_email.protocol
      endpoint  = aws_sns_topic_subscription.pulse_alert_email.endpoint
    }
  }
}
