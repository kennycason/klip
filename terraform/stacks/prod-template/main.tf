module "klip" {
  source                  = "../../modules/klip"
  stack                   = local.stack
  service_id              = local.service_id
  service_name            = local.service_name
  region                  = local.region
  klip_log_level          = local.klip.log_level
  klip_port               = local.klip.port
  klip_enabled            = local.klip.enabled
  klip_canvas_enabled     = local.klip.canvas_enabled
  klip_admin_enabled      = local.klip.admin_enabled
  klip_admin_api_key      = local.klip.admin_api_key
  klip_s3_cache_enabled   = local.klip.s3_cache_enabled
  klip_s3_bucket          = local.klip.s3_bucket
  klip_s3_cache_bucket    = local.klip.s3_cache_bucket
  klip_s3_cache_folder    = local.klip.s3_cache_folder
  klip_rules              = local.klip.rules
  klip_rules_file         = local.klip.rules_file
  klip_canvas_rules       = local.klip.canvas_rules
  klip_canvas_rules_file  = local.klip.canvas_rules_file
  klip_gm_timeout_seconds = local.klip.gm_timeout_seconds
  klip_gm_memory_limit    = local.klip.gm_memory_limit
  klip_gm_map_limit       = local.klip.gm_map_limit
  klip_gm_disk_limit      = local.klip.gm_disk_limit
  klip_gm_pool_size       = local.klip.gm_pool_size
  tags                    = local.tags
  private_subnets         = local.private_subnets
  public_subnets          = local.public_subnets
  vpc_id                  = local.vpc_id
  route53_domain          = local.route53_domain
  route53_zone_id         = local.route53_zone_id
  node_count              = local.node_count
  node_cpu                = local.node_cpu
  node_memory             = local.node_memory
}
