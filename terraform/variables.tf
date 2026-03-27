variable "project_id" {
  type    = string
  default = "notification-system-lab"
}

variable "region" {
  type    = string
  default = "asia-east1"
}

variable "db_name" {
  type    = string
  default = "lab_db"
}

variable "db_username" {
  type    = string
  default = "lab-user"
}

variable "db_password" {
  type      = string
  sensitive = true
}
