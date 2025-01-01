locals {
  stack        = "prod"
  service_id   = "klip-${local.stack}"
  service_name = "klip"
  region       = "us-west-2"
  account_id   = "<AWS_ACCOUNT_ID>"
  klip         = {
    log_level         = "INFO"
    port              = "8080"
    enabled           = "true"
    canvas_enabled    = "true"
    admin_enabled     = "false"
    admin_api_key     = ""  # required if admin_enabled=true
    s3_cache_enabled  = "true"
    s3_bucket         = "cdn.klip.com"
    s3_cache_bucket   = "cdn.klip.com"
    s3_cache_folder   = "_cache/"
    rules             = ""
    rules_file        = ""
    canvas_rules      = ""
    canvas_rules_file = ""

    # GraphicsMagick config
    gm_timeout_seconds = "30"
    gm_memory_limit    = "128MB"
    gm_map_limit       = "256MB"
    gm_disk_limit      = "512GB"
    gm_pool_size       = ""  # defaults to available processors if not set
  }
  fargate_cpu     = 2048
  fargate_memory  = 2048
  vpc_id          = "vpc-<ID>"
  vpn_subnet_id   = "subnet-<ID>"
  private_subnets = [
    "subnet-<ID1>", "subnet-<ID2>"
  ]
  public_subnets = [
    "subnet-<ID1>", "subnet-<ID2>"
  ]
  route53_domain  = "klip.klip.com"
  route53_zone_id = "<ZONE_ID>"

  tags = {
    stack        = local.stack
    service_id   = local.service_id
    managed_with = "terraform"
  }
}
