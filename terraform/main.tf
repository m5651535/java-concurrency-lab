provider "google" {
  project = var.project_id
  region  = var.region
}

# 啟用所需的 GCP APIs
resource "google_project_service" "apis" {
  for_each = toset([
    "sqladmin.googleapis.com",
    "redis.googleapis.com",
    "artifactregistry.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
    "iam.googleapis.com",
    "vpcaccess.googleapis.com",
  ])

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

#### VPC Connector
# 注意：如果你本地已經有 vpc-con，在 apply 之前先執行：
# terraform import google_vpc_access_connector.vpc_con \
#   projects/notification-system-lab/locations/asia-east1/connectors/vpc-con
resource "google_vpc_access_connector" "vpc_con" {
  name          = "vpc-con"
  region        = var.region
  network       = "default"
  ip_cidr_range = "10.8.0.0/28"
  min_instances = 2
  max_instances = 10
  machine_type    = "e2-micro"
  max_throughput  = 1000

  depends_on = [google_project_service.apis]
}

#### Cloud SQL Instance（新建，便宜規格）
resource "google_sql_database_instance" "concurrency_lab_db" {
  name             = "concurrency-lab-db"
  database_version = "POSTGRES_15"
  region           = var.region
  deletion_protection = false

  settings {
    tier              = "db-f1-micro"
    disk_size         = 10
    disk_type         = "PD_SSD"
    availability_type = "ZONAL"

    backup_configuration {
      enabled = false
    }

    ip_configuration {
      ipv4_enabled = true
    }
  }

  depends_on = [google_project_service.apis]
}

#### Cloud SQL Database
resource "google_sql_database" "database" {
  name     = var.db_name
  instance = google_sql_database_instance.concurrency_lab_db.name
}

#### Cloud SQL User
resource "google_sql_user" "users" {
  name     = var.db_username
  instance = google_sql_database_instance.concurrency_lab_db.name
  password = var.db_password
}

#### Memorystore Redis（新建）
resource "google_redis_instance" "concurrency_lab_redis" {
  name           = "concurrency-lab-redis"
  tier           = "BASIC"
  memory_size_gb = 1
  region         = var.region
  redis_version  = "REDIS_7_0"
  authorized_network = "default"

  depends_on = [google_project_service.apis]
}

#### Artifact Registry Repository
resource "google_artifact_registry_repository" "concurrency_lab_repo" {
  location      = var.region
  repository_id = "concurrency-lab"
  description   = "Docker repository for Concurrency Lab"
  format        = "DOCKER"

  depends_on = [google_project_service.apis]
}

#### Service Account
resource "google_service_account" "deployer" {
  account_id   = "concurrency-lab-deployer"
  display_name = "Concurrency Lab GitHub Actions Deployer"

  depends_on = [google_project_service.apis]
}

#### IAM Roles
resource "google_project_iam_member" "artifact_writer" {
  project = var.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.deployer.email}"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "run_admin" {
  project = var.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.deployer.email}"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "sa_user" {
  project = var.project_id
  role    = "roles/iam.serviceAccountUser"
  member  = "serviceAccount:${google_service_account.deployer.email}"

  depends_on = [google_project_service.apis]
}

resource "google_project_iam_member" "cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.deployer.email}"
  depends_on = [google_project_service.apis]
}

#### Service Account Key
resource "google_service_account_key" "deployer_key" {
  service_account_id = google_service_account.deployer.name

  depends_on = [google_project_service.apis]
}

#### Secret Manager - DB_USERNAME
resource "google_secret_manager_secret" "db_username" {
  secret_id = "CONCURRENCY_DB_USERNAME"
  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "db_username_version" {
  secret      = google_secret_manager_secret.db_username.id
  secret_data = var.db_username

  depends_on = [google_project_service.apis]
}

#### Secret Manager - DB_PASSWORD
resource "google_secret_manager_secret" "db_password" {
  secret_id = "CONCURRENCY_DB_PASSWORD"
  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_version" "db_password_version" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = var.db_password

  depends_on = [google_project_service.apis]
}
