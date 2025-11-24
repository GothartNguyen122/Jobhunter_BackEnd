# Phân Tích Hiệu Năng Backend - JobHunter

## 🔴 Vấn Đề Nghiêm Trọng (Cần Tối Ưu Ngay)

### 1. **JobService.fetchMatchingJobsByJobAlert()** - CRITICAL
**File:** `JobService.java:536-600`

**Vấn đề:**
- Load TẤT CẢ jobs active vào memory (line 566: `findAll(baseSpec)`)
- Filter trong memory sau đó mới paginate
- Nếu có 10,000 jobs → load 10,000 records vào memory → OOM risk

**Impact:** 
- Memory: Rất cao (có thể hàng trăm MB)
- CPU: Cao (filter trong memory)
- Response time: Chậm khi có nhiều jobs

**Giải pháp:**
- Chuyển logic filter vào Specification/Query
- Sử dụng database pagination thay vì memory pagination
- Chỉ load jobs cần thiết cho page hiện tại

---

### 2. **JobAlertService.fetchAllActiveJobs()** - HIGH
**File:** `JobAlertService.java:180-191`

**Vấn đề:**
- Load TẤT CẢ active jobs không có pagination
- Được gọi trong scheduled job (mỗi ngày) và các method khác
- Nếu có 10,000 jobs → load tất cả vào memory

**Impact:**
- Memory: Cao
- Scheduled job có thể chậm hoặc fail

**Giải pháp:**
- Thêm limit hoặc batch processing
- Chỉ load jobs cần thiết cho từng alert

---

### 3. **UserRepository.findAllWithSkills()** - MEDIUM
**File:** `UserRepository.java:26-27`

**Vấn đề:**
- Load TẤT CẢ users với skills, không có pagination
- Có thể có N+1 query nếu không dùng đúng

**Impact:**
- Memory: Trung bình (tùy số lượng users)
- Nếu có 100,000 users → rất chậm

**Giải pháp:**
- Thêm pagination
- Kiểm tra xem method này có được sử dụng không

---

### 4. **JobAlertRepository.findActiveAlertsWithSkills()** - MEDIUM
**File:** `JobAlertRepository.java:19-21`

**Vấn đề:**
- Load TẤT CẢ active alerts không có pagination
- Được gọi trong scheduled job

**Impact:**
- Memory: Trung bình (tùy số lượng alerts)
- Scheduled job có thể chậm

**Giải pháp:**
- Batch processing trong scheduled job
- Process từng batch alerts

---

## ✅ Điểm Tốt

1. **JobRepository** - Sử dụng EntityGraph để tránh N+1 queries
2. **JobService.fetchAll()** - Sử dụng pagination đúng cách
3. **JobAlertService** - Đã tối ưu với pre-calculated skill IDs
4. **Most services** - Sử dụng Specification và pagination

---

## 📊 Đề Xuất Tối Ưu

### Priority 1 (Critical):
1. Tối ưu `JobService.fetchMatchingJobsByJobAlert()` - chuyển filter vào database query
2. Tối ưu `JobAlertService.fetchAllActiveJobs()` - thêm limit hoặc batch processing

### Priority 2 (High):
3. Kiểm tra và tối ưu `UserRepository.findAllWithSkills()` - thêm pagination nếu cần
4. Tối ưu scheduled job - batch processing

### Priority 3 (Medium):
5. Thêm database indexes cho các query thường dùng
6. Monitor query performance với logging

---

## 🔍 Database Indexes Cần Kiểm Tra

1. `jobs.active` + `jobs.endDate` (composite index)
2. `jobs.company_id` (foreign key)
3. `job_alerts.active` + `job_alerts.user_id`
4. `users.email` (unique index)
5. `job_skills` join table indexes

---

## 📈 Metrics Cần Monitor

1. Query execution time
2. Memory usage
3. Number of records loaded
4. Database connection pool usage
5. Response time của các API endpoints

