resource "aws_cloudwatch_log_group" "pulse" {
  name              = "/pulse-backend/production"
  retention_in_days = 30
}

resource "aws_cloudwatch_metric_alarm" "high_failure_rate" {
  alarm_name          = "pulse-high-failure-rate"
  alarm_description   = "API check failure count exceeded 10 in 5 minutes"
  namespace           = "PulseBackend"
  metric_name         = "api.check.total"
  dimensions          = { result = "failure" }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.pulse_alert.arn]
  ok_actions          = [aws_sns_topic.pulse_alert.arn]
}

resource "aws_cloudwatch_metric_alarm" "high_latency" {
  alarm_name          = "pulse-high-latency"
  alarm_description   = "API check p99 latency exceeded 5s"
  namespace           = "PulseBackend"
  metric_name         = "api.check.latency"
  dimensions          = { result = "success" }
  extended_statistic  = "p99"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 5000
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.pulse_alert.arn]
  ok_actions          = [aws_sns_topic.pulse_alert.arn]
}

resource "aws_cloudwatch_dashboard" "pulse" {
  dashboard_name = "pulse-backend"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "API Check Results (5m sum)"
          view    = "timeSeries"
          region  = var.aws_region
          period  = 300
          stat    = "Sum"
          metrics = [
            ["PulseBackend", "api.check.total", "result", "success", { label = "Success" }],
            ["PulseBackend", "api.check.total", "result", "failure", { label = "Failure", color = "#d62728" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "API Check Latency"
          view    = "timeSeries"
          region  = var.aws_region
          period  = 300
          metrics = [
            ["PulseBackend", "api.check.latency", "result", "success", { stat = "p50", label = "p50" }],
            ["PulseBackend", "api.check.latency", "result", "success", { stat = "p95", label = "p95" }],
            ["PulseBackend", "api.check.latency", "result", "success", { stat = "p99", label = "p99", color = "#d62728" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 6
        height = 4
        properties = {
          title   = "Scheduled API Count"
          view    = "singleValue"
          region  = var.aws_region
          period  = 60
          stat    = "Average"
          metrics = [
            ["PulseBackend", "api.scheduled.count", { label = "Active Monitors" }]
          ]
        }
      },
      {
        type   = "alarm"
        x      = 6
        y      = 6
        width  = 18
        height = 4
        properties = {
          title = "Alarm Status"
          alarms = [
            aws_cloudwatch_metric_alarm.high_failure_rate.arn,
            aws_cloudwatch_metric_alarm.high_latency.arn
          ]
        }
      }
    ]
  })
}
