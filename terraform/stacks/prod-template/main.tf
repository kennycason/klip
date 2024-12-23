module "klip" {
  source          = "../../modules/klip"
  stack           = local.stack
  service_id      = local.service_id
  service_name    = local.service_name
  region          = local.region
  allowed_sources = local.allowed_sources
  tags            = local.tags
  private_subnets = local.private_subnets
  public_subnets  = local.public_subnets
  vpc_id          = local.vpc_id
}
