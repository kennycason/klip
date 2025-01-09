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

variable "klip_log_level" {
  type    = string
  default = "INFO"
}

variable "klip_port" {
  type    = string
  default = "8080"
}

variable "klip_enabled" {
  type    = string
  default = "true"
}

variable "klip_canvas_enabled" {
  type    = string
  default = "true"
}

variable "klip_admin_enabled" {
  type    = string
  default = "false"
}

variable "klip_admin_api_key" {
  type    = string
  default = ""
}

variable "klip_s3_bucket" {
  type = string
}

variable "klip_s3_cache_enabled" {
  type = string
}

variable "klip_s3_cache_bucket" {
  type = string
}

variable "klip_s3_cache_folder" {
  type    = string
  default = "_cache/"
}


variable "klip_rules" {
  type = string
}

variable "klip_rules_file" {
  type = string
}

variable "klip_canvas_rules" {
  type    = string
  default = ""
}

variable "klip_canvas_rules_file" {
  type    = string
  default = ""
}

variable "klip_gm_timeout_seconds" {
  type = string
  default = "30"
}

variable "klip_gm_memory_limit" {
  type = string
  default = "256MB"
}

variable "klip_gm_map_limit" {
  type = string
  default = "512MB"
}

variable "klip_gm_disk_limit" {
  type = string
  default = "1GB"
}

variable "klip_gm_pool_size" {
  type = string
  default = ""  # defaults to available processors if not set
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

variable "route53_domain" {
  type = string
}

variable "route53_zone_id" {
  type = string
}

variable "node_count" {
  type = number
}

variable "node_cpu" {
  type = number
}

variable "node_memory" {
  type = number
}

variable "tags" {
  type    = map(string)
  default = {}
}
