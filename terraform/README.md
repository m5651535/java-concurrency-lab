# Concurrency Lab Infrastructure with Terraform

這是一個全新的基礎設施，從零建立所有 Concurrency Lab 需要的 GCP 資源。

## 前置需求
- terraform CLI >= 1.5.0
- gcloud CLI 已登入並設定正確 project
- 所有必要的 GCP APIs 會由 Terraform 自動啟用

## 初始化
```bash
cd terraform
terraform init
```

## 如果你已經有現有的 vpc-con（先 import 再 apply）
```bash
terraform import google_vpc_access_connector.vpc_con \
  projects/notification-system-lab/locations/asia-east1/connectors/vpc-con

terraform plan -var="db_password=你的密碼"
terraform apply -var="db_password=你的密碼"
```

## 全新環境（沒有任何現有資源，直接 apply）
```bash
terraform plan -var="db_password=你的密碼"
terraform apply -var="db_password=你的密碼"
```

## 取得 GitHub Secrets 所需的值
```bash
# SA Key → 加到 GitHub Secret: GCP_SA_KEY
terraform output -raw sa_key_base64

# Redis Host → 加到 GitHub Secret: REDIS_HOST
terraform output -raw redis_host

# Cloud SQL Connection Name → 更新 deploy.yml 的 CLOUD_SQL_CONNECTION
terraform output cloud_sql_connection_name
```

## 銷毀所有資源（節省費用）
```bash
terraform destroy -var="db_password=你的密碼"
```

## 注意事項
- db_password 沒有預設值，每次執行都必須明確傳入
- .tfvars 已加入 .gitignore，請勿 commit 任何密碼
- Cloud SQL 建立需要約 5-10 分鐘，請耐心等待
- 銷毀前請確認已備份重要實驗數據
- vpc-con 如果是全新環境會自動建立，如果已存在請先執行 terraform import 再 apply
