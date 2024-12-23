terraform {
  required_version = ">= 1.1.4"

  # instead of "klip-infrastructure-prod", you may choose to put all your platform config into a single bucket per environment.
  backend "s3" {
    bucket = "klip-infrastructure-prod"
    key    = "terraform/klip/stack-prod"
    region = "us-west-2"

    # used to lock access to state files
    dynamodb_table = "klip-infrastructure-tf-state-locks"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.67"
    }
  }
}

provider "aws" {
  region = local.region
}
