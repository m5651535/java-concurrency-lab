output "sa_key_base64" {
  value       = google_service_account_key.deployer_key.private_key
  sensitive   = true
  description = "Base64 encoded SA key，用來設定 GitHub Secret GCP_SA_KEY"
}

output "artifact_registry_url" {
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/concurrency-lab"
  description = "Docker image 推送的完整 registry URL"
}

output "cloud_sql_connection_name" {
  value       = google_sql_database_instance.concurrency_lab_db.connection_name
  description = "Cloud Run --add-cloudsql-instances 需要這個值"
}

output "redis_host" {
  value       = google_redis_instance.concurrency_lab_redis.host
  sensitive   = true
  description = "Memorystore Redis 私有 IP，給 application-cloudrun.yml 使用"
}
