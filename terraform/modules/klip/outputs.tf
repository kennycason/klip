output "alb_dns_name" {
  value = aws_lb.klip_lb.dns_name
}

output "https_url" {
  value = "https://${var.route53_domain}"
}
