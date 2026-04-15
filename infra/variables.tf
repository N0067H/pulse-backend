variable "aws_region" {
  type        = string
  description = "AWS region to deploy resources"
  default     = "us-east-1"
}

variable "alert_email" {
  type        = string
  description = "SNS alert email"
}
