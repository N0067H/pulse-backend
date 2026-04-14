variable "aws_region" {
  type        = string
  description = "AWS region to deploy resources"
  default     = "ap-northeast-2"
}

variable "alert_email" {
  type        = string
  description = "SNS alert email"
}
