# ECR Repository
resource "aws_ecr_repository" "klip_repo" {
  name                 = "${var.service_name}-${var.stack}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
  }

  tags = var.tags
}

# IAM Policy for S3 Access
resource "aws_iam_policy" "ecs_s3_custom_policy" {
  name = "${var.service_name}-${var.stack}-ecs-s3-custom-policy"

  # TODO remove "s3:PutObject" if not needed (ie only add to cache bucket)
  policy = jsonencode({
    Version = "2012-10-17", Statement = concat(
      [
        {
          Effect = "Allow", Action = [
          "s3:GetObject", "s3:ListBucket", "s3:PutObject"
        ], Resource = [
          "arn:aws:s3:::${var.klip_s3_bucket}", "arn:aws:s3:::${var.klip_s3_bucket}/*"
        ]
        }
      ], var.klip_s3_cache_bucket != var.klip_s3_bucket ? [
        {
          Effect = "Allow", Action = ["s3:GetObject", "s3:ListBucket", "s3:PutObject"], Resource = [
          "arn:aws:s3:::${var.klip_s3_cache_bucket}", "arn:aws:s3:::${var.klip_s3_cache_bucket}/*"
        ]
        }
      ] : []
    )
  })
}

# ECS Task Execution Role
resource "aws_iam_role" "ecs_task_execution_role" {
  name = "${var.service_name}-${var.stack}-ecs-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole"
      }
    ]
  })
}

# ECS Task Role
resource "aws_iam_role" "ecs_task_role" {
  name = "${var.service_name}-${var.stack}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17", Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

# Attach Policies to ECS Roles
resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy_attachment" "ecs_s3_access_policy" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_s3_custom_policy.arn
}

resource "aws_iam_role_policy_attachment" "ecs_cloudwatch_logs" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess"
}

# ECS Cluster
resource "aws_ecs_cluster" "klip_cluster" {
  name = "${var.service_name}-${var.stack}-cluster"
  tags = var.tags
}

# CloudWatch Logs
resource "aws_cloudwatch_log_group" "ecs_log_group" {
  name              = "/ecs/${var.service_name}-${var.stack}"
  retention_in_days = 14
  tags              = var.tags
}

# Security Group for ALB
resource "aws_security_group" "alb_sg" {
  name        = "klip-prod-alb-sg"
  description = "Allow HTTP and HTTPS access for ALB"
  vpc_id      = var.vpc_id

  lifecycle {
    ignore_changes = [description, tags, egress, ingress]
  }

  tags = var.tags
}

# ALB Security Group Rules
resource "aws_security_group_rule" "alb_ingress" {
  type              = "ingress"
  from_port         = 80
  to_port           = 80
  protocol          = "tcp"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.alb_sg.id

  lifecycle {
    ignore_changes = all
  }
}

resource "aws_security_group_rule" "alb_egress" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.alb_sg.id

  lifecycle {
    ignore_changes = all
  }
}

# Security Group for ECS Tasks
resource "aws_security_group" "ecs_tasks_sg" {
  name        = "${var.service_name}-${var.stack}-ecs-tasks-sg"
  description = "Allow inbound traffic from ALB to ECS tasks"
  vpc_id      = var.vpc_id

  lifecycle {
    create_before_destroy = true
    ignore_changes = [ingress] # Ignore manual changes in AWS (while testing)
  }

  tags = var.tags
}

# ECS Tasks Security Group Rules
resource "aws_security_group_rule" "ecs_ingress" {
  type                     = "ingress"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb_sg.id
  security_group_id        = aws_security_group.ecs_tasks_sg.id

  lifecycle {
    ignore_changes = all
  }
}

resource "aws_security_group_rule" "ecs_egress" {
  type              = "egress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.ecs_tasks_sg.id

  lifecycle {
    ignore_changes = all
  }
}


# ECS Task Definition
resource "aws_ecs_task_definition" "klip_task" {
  family                   = "${var.service_name}-${var.stack}-task"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 1024
  memory                   = 2048
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name      = "klip-container"
      image     = "${aws_ecr_repository.klip_repo.repository_url}:latest"
      essential = true

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${var.klip_port}/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }

      environment = [
        { name = "KLIP_HTTP_PORT", value = var.klip_port },
        { name = "KLIP_AWS_REGION", value = var.region },
        { name = "KLIP_S3_BUCKET", value = var.klip_s3_bucket },
        { name = "KLIP_CACHE_BUCKET", value = var.klip_s3_cache_bucket },
        { name = "KLIP_CACHE_FOLDER", value = var.klip_s3_cache_folder },
      ]

      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options   = {
          awslogs-group         = "/ecs/${var.service_name}-${var.stack}"
          awslogs-region        = var.region
          awslogs-stream-prefix = "ecs"
        }
      }

      ulimits = [
        {
          name      = "nofile"
          softLimit = 65536
          hardLimit = 65536
        }
      ]
    }
  ])

  lifecycle {
    create_before_destroy = true
  }
}

# ECS Service
resource "aws_ecs_service" "klip_service" {
  name            = "${var.service_name}-${var.stack}-service"
  cluster         = aws_ecs_cluster.klip_cluster.id
  task_definition = aws_ecs_task_definition.klip_task.arn
  desired_count   = 2
  launch_type     = "FARGATE"
#  enable_execute_command = true # enable ssh access

  lifecycle {
    create_before_destroy = true
  }

  network_configuration {
    subnets          = var.private_subnets
    security_groups  = [aws_security_group.ecs_tasks_sg.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.klip_tg.arn
    container_name   = "klip-container"
    container_port   = 8080
  }

  depends_on = [
    aws_lb_listener.http_listener,
    aws_security_group.alb_sg,
    aws_security_group.ecs_tasks_sg,
    aws_security_group_rule.alb_ingress,
    aws_security_group_rule.alb_egress,
    aws_security_group_rule.ecs_ingress,
    aws_security_group_rule.ecs_egress
  ]
}

# ALB
resource "aws_lb" "klip_lb" {
  name               = "${var.service_name}-${var.stack}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb_sg.id]
  subnets            = var.public_subnets

  lifecycle {
    create_before_destroy = true
  }

  depends_on = [
    aws_security_group.alb_sg
  ]

  tags = var.tags
}

# Target Group
resource "aws_lb_target_group" "klip_tg" {
  name        = "${var.service_name}-${var.stack}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  lifecycle {
    create_before_destroy = true
  }

  health_check {
    path                = "/health"
    port                = "8080"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 3
    unhealthy_threshold = 2
    matcher             = "200"
  }

  tags = var.tags
}

# Listener
resource "aws_lb_listener" "http_listener" {
  load_balancer_arn = aws_lb.klip_lb.arn
  port              = 80
  protocol          = "HTTP"

  lifecycle {
    create_before_destroy = true
  }

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.klip_tg.arn
  }
}

# Route 53 Record
resource "aws_route53_record" "klip_dns" {
  zone_id = var.route53_zone_id
  name    = var.route53_domain
  type    = "A"

  alias {
    name                   = aws_lb.klip_lb.dns_name
    zone_id                = aws_lb.klip_lb.zone_id
    evaluate_target_health = true
  }
}
