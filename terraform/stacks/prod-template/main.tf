module "klip" {
  source                     = "../../modules/klip"
  stack                      = local.stack
  service_id                 = local.service_id
  service_name               = local.service_name
  region                     = local.region
  klip_log_level             = local.klip.log_level
  klip_port                  = local.klip.port
  klip_s3_cache_enabled      = local.klip.s3_cache_enabled
  klip_s3_bucket             = local.klip.s3_bucket
  klip_s3_cache_bucket       = local.klip.s3_cache_bucket
  klip_s3_cache_folder       = local.klip.s3_cache_folder
  klip_rules                 = local.klip.rules
  klip_rules_file            = local.klip.rules_file
  klip_rules_validation_mode = local.klip.rules_validation_mode
  tags                       = local.tags
  private_subnets            = local.private_subnets
  public_subnets             = local.public_subnets
  vpc_id                     = local.vpc_id
  route53_domain             = local.route53_domain
  route53_zone_id            = local.route53_zone_id
}
