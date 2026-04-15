output "ec2" {
  value = {
    public_ip   = aws_instance.pulse.public_ip
    public_dns  = aws_instance.pulse.public_dns
    instance_id = aws_instance.pulse.id
  }
}

output "dynamodb_tables" {
  value = {
    apis          = { name = aws_dynamodb_table.apis.name }
    check_results = { name = aws_dynamodb_table.check_results.name }
  }
}

output "sns" {
  value = {
    pulse_alert = {
      name     = aws_sns_topic.pulse_alert.name
      arn      = aws_sns_topic.pulse_alert.arn
      protocol = aws_sns_topic_subscription.pulse_alert_email.protocol
      endpoint = aws_sns_topic_subscription.pulse_alert_email.endpoint
    }
  }
}
