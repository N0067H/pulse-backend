variable "aws_region" {
  type        = string
  description = "AWS region to deploy resources"
  default     = "us-east-1"
}

variable "alert_email" {
  type        = string
  description = "SNS alert email"
}

variable "github_repo" {
  type        = string
  description = "GitHub repository in 'owner/repo' format (e.g. noobth/pulse-backend)"
}
