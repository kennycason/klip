variable "stack" {
  description = "Environment name (e.g., prod, staging, dev)"
  type        = string
}

variable "service_id" {
  description = "Service name + stack"
  type        = string
}

variable "service_name" {
  type = string
}

variable "region" {
  type = string
}

variable "klip_port" {
  type    = number
  default = 8080
}

variable "klip_s3_bucket" {
  type = string
}


variable "klip_s3_cache_bucket" {
  type = string
}

variable "klip_s3_cache_folder" {
  type    = string
  default = "_cache/"
}

variable "vpc_id" {
  type = string
}

variable "public_subnets" {
  type = list(string)
}

variable "private_subnets" {
  type = list(string)
}

variable "tags" {
  type    = map(string)
  default = {}
}
