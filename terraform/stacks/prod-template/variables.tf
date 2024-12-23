locals {
  stack        = "prod"
  service_id   = "klip-${local.stack}"
  service_name = "klip"
  region       = "us-west-2"
  account_id   = "<AWS_ACCOUNT_ID>"
  klip         = {
    s3_bucket       = "cdn.klip.com"
    s3_cache_bucket = "cdn.klip.com"
    s3_cache_folder = "_cache/"
    port            = "8080"
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
