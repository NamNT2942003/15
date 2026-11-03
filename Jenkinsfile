pipeline {
    agent any

    stages {
        // =================================================================
        // Stage 1: Validate Config (Bài tập 6: Fail-Fast Gatekeeper)
        // Kiểm tra toàn diện cú pháp prometheus.yml và rules trước khi deploy
        // =================================================================
        stage('Validate Config') {
            steps {
                echo '=== BẮT ĐẦU KIỂM TRA TÍNH HỢP LỆ CỦA CẤU HÌNH PROMETHEUS ==='
                script {
                    // Chạy promtool trong container prom/prometheus để không phụ thuộc máy chủ Jenkins
                    sh '''
                        docker run --rm -v "${WORKSPACE}/prometheus:/etc/prometheus" \
                            prom/prometheus:v2.51.0 promtool check config /etc/prometheus/prometheus.yml
                    '''
                    echo '>>> Cấu hình Prometheus và Rules hoàn toàn hợp lệ! Tiếp tục deploy...'
                }
            }
        }

        // =================================================================
        // Stage 2: Deploy
        // Chỉ được thực thi khi Stage "Validate Config" PASS 100%
        // =================================================================
        stage('Deploy') {
            steps {
                echo '=== BẮT ĐẦU TRIỂN KHAI RELOAD / RESTART PROMETHEUS ==='
                sh '''
                    # Ưu tiên reload zero-downtime qua API, nếu không phản hồi mới restart container
                    curl -s -X POST http://localhost:9090/-/reload || docker restart prometheus
                '''
                echo '>>> Triển khai cấu hình giám sát thành công!'
            }
        }
    }

    post {
        failure {
            echo 'CRITICAL: Pipeline thất bại tại bước Validate! Không thực hiện restart container để bảo vệ hệ thống.'
        }
        success {
            echo 'SUCCESS: Toàn bộ quy trình kiểm tra và triển khai giám sát hoàn tất thành công.'
        }
    }
}
