resource "aws_dynamodb_table" "apis" {
  name         = "apis"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "api_id"

  attribute {
    name = "api_id"
    type = "S"
  }
}

resource "aws_dynamodb_table" "check_results" {
  name         = "check_results"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "api_id"
  range_key    = "checked_at"

  attribute {
    name = "api_id"
    type = "S"
  }

  attribute {
    name = "checked_at"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
}
