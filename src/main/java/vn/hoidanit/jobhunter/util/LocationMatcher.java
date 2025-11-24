package vn.hoidanit.jobhunter.util;

import java.util.*;

/**
 * Utility class để so sánh và match location (tỉnh/thành phố) một cách thông minh
 * Hỗ trợ matching các biến thể tên gọi như "TP.HCM", "Hồ Chí Minh", "Ho Chi Minh", v.v.
 */
public class LocationMatcher {

    /**
     * Map các tỉnh/thành phố với các tên gọi phổ biến
     * Key: Tên chính thức
     * Value: Set các tên gọi khác (aliases)
     */
    private static final Map<String, Set<String>> LOCATION_ALIASES = new HashMap<>();

    static {
        // Hà Nội
        addLocation("Hà Nội", "Ha Noi", "Hanoi", "Thủ đô Hà Nội", "Thu do Ha Noi");

        // Hồ Chí Minh
        addLocation("Hồ Chí Minh", "Ho Chi Minh", "TP.HCM", "TP HCM", "Sài Gòn", "Sai Gon", 
                   "Thành phố Hồ Chí Minh", "Thanh pho Ho Chi Minh", "HCM", "TP.HCM", "TPHCM");

        // Hải Phòng
        addLocation("Hải Phòng", "Hai Phong", "HP");

        // Đà Nẵng
        addLocation("Đà Nẵng", "Da Nang", "DN");

        // Cần Thơ
        addLocation("Cần Thơ", "Can Tho", "CT");

        // Bà Rịa - Vũng Tàu
        addLocation("Bà Rịa - Vũng Tàu", "Ba Ria - Vung Tau", "Vũng Tàu", "Vung Tau", 
                   "BR-VT", "BRVT", "Bà Rịa Vũng Tàu", "Ba Ria Vung Tau");

        // Bình Dương
        addLocation("Bình Dương", "Binh Duong", "BD");

        // Đồng Nai
        addLocation("Đồng Nai", "Dong Nai", "DN2");

        // Khánh Hòa
        addLocation("Khánh Hòa", "Khanh Hoa", "Nha Trang", "KH");

        // Lâm Đồng
        addLocation("Lâm Đồng", "Lam Dong", "Đà Lạt", "Da Lat", "LD");

        // Quảng Ninh
        addLocation("Quảng Ninh", "Quang Ninh", "Hạ Long", "Ha Long", "QN");

        // Thừa Thiên Huế
        addLocation("Thừa Thiên Huế", "Thua Thien Hue", "Huế", "Hue", "HUE");

        // Nghệ An
        addLocation("Nghệ An", "Nghe An", "Vinh", "NA");

        // Các tỉnh khác (có thể bổ sung thêm)
        addLocation("An Giang", "AG");
        addLocation("Bạc Liêu", "Bac Lieu", "BL");
        addLocation("Bắc Kạn", "Bac Kan", "BK");
        addLocation("Bắc Giang", "Bac Giang", "BG");
        addLocation("Bắc Ninh", "Bac Ninh", "BN");
        addLocation("Bến Tre", "Ben Tre", "BT");
        addLocation("Bình Định", "Binh Dinh", "BD2");
        addLocation("Bình Phước", "Binh Phuoc", "BP");
        addLocation("Bình Thuận", "Binh Thuan", "BT2");
        addLocation("Cà Mau", "Ca Mau", "CM");
        addLocation("Cao Bằng", "Cao Bang", "CB");
        addLocation("Đắk Lắk", "Dak Lak", "Đắc Lắc", "DL");
        addLocation("Đắk Nông", "Dak Nong", "Đắc Nông", "DG");
        addLocation("Điện Biên", "Dien Bien", "DB");
        addLocation("Đồng Tháp", "Dong Thap", "DT");
        addLocation("Gia Lai", "GL");
        addLocation("Hà Giang", "Ha Giang", "HG");
        addLocation("Hà Nam", "Ha Nam", "HN2");
        addLocation("Hà Tĩnh", "Ha Tinh", "HT");
        addLocation("Hải Dương", "Hai Duong", "HD");
        addLocation("Hòa Bình", "Hoa Binh", "HB");
        addLocation("Hưng Yên", "Hung Yen", "HY");
        addLocation("Kiên Giang", "Kien Giang", "KG");
        addLocation("Lào Cai", "Lao Cai", "LC");
        addLocation("Lạng Sơn", "Lang Son", "LS");
        addLocation("Long An", "LA");
        addLocation("Nam Định", "Nam Dinh", "ND");
        addLocation("Ninh Bình", "Ninh Binh", "NB");
        addLocation("Ninh Thuận", "Ninh Thuan", "NT");
        addLocation("Phú Thọ", "Phu Tho", "PT");
        addLocation("Phú Yên", "Phu Yen", "PY");
        addLocation("Quảng Bình", "Quang Binh", "QB");
        addLocation("Quảng Nam", "Quang Nam", "QN");
        addLocation("Quảng Ngãi", "Quang Ngai", "QG");
        addLocation("Quảng Trị", "Quang Tri", "QT");
        addLocation("Sóc Trăng", "Soc Trang", "ST");
        addLocation("Sơn La", "Son La", "SL");
        addLocation("Tây Ninh", "Tay Ninh", "TN");
        addLocation("Thái Bình", "Thai Binh", "TB");
        addLocation("Thái Nguyên", "Thai Nguyen", "TY");
        addLocation("Thanh Hóa", "Thanh Hoa", "TH");
        addLocation("Tiền Giang", "Tien Giang", "TG");
        addLocation("Trà Vinh", "Tra Vinh", "TV");
        addLocation("Tuyên Quang", "Tuyen Quang", "TQ");
        addLocation("Vĩnh Long", "Vinh Long", "VL");
        addLocation("Vĩnh Phúc", "Vinh Phuc", "VP");
        addLocation("Yên Bái", "Yen Bai", "YB");
    }

    private static void addLocation(String mainName, String... aliases) {
        Set<String> aliasSet = new HashSet<>();
        aliasSet.add(normalize(mainName));
        for (String alias : aliases) {
            aliasSet.add(normalize(alias));
        }
        LOCATION_ALIASES.put(normalize(mainName), aliasSet);
    }

    /**
     * Normalize location string: loại bỏ dấu, chuyển về lowercase, trim
     */
    private static String normalize(String location) {
        if (location == null) {
            return "";
        }
        return location.trim()
                .toLowerCase()
                .replaceAll("\\s+", " ") // Nhiều khoảng trắng thành 1
                .replaceAll("[^a-z0-9\\s]", ""); // Loại bỏ ký tự đặc biệt
    }

    /**
     * Kiểm tra xem hai location có match nhau không
     * Hỗ trợ matching các biến thể tên gọi
     */
    public static boolean matches(String location1, String location2) {
        if (location1 == null || location2 == null) {
            return false;
        }

        String norm1 = normalize(location1);
        String norm2 = normalize(location2);

        // Exact match sau khi normalize
        if (norm1.equals(norm2)) {
            return true;
        }

        // Kiểm tra trong alias map
        Set<String> aliases1 = findAliases(norm1);
        Set<String> aliases2 = findAliases(norm2);

        // Nếu cả hai đều có trong map, so sánh sets
        if (!aliases1.isEmpty() && !aliases2.isEmpty()) {
            // Có overlap nghĩa là cùng một tỉnh
            return !Collections.disjoint(aliases1, aliases2);
        }

        // Nếu một trong hai có trong map, kiểm tra xem cái kia có trong aliases không
        if (!aliases1.isEmpty()) {
            return aliases1.contains(norm2);
        }
        if (!aliases2.isEmpty()) {
            return aliases2.contains(norm1);
        }

        // Fuzzy matching: kiểm tra xem một location có chứa location kia không
        // VD: "TP.HCM" chứa "HCM", "Hồ Chí Minh" chứa "HCM"
        if (norm1.contains(norm2) || norm2.contains(norm1)) {
            // Chỉ match nếu độ dài chênh lệch không quá lớn (tránh false positive)
            int lengthDiff = Math.abs(norm1.length() - norm2.length());
            if (lengthDiff <= 5) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tìm tất cả aliases của một location
     */
    private static Set<String> findAliases(String normalizedLocation) {
        for (Map.Entry<String, Set<String>> entry : LOCATION_ALIASES.entrySet()) {
            if (entry.getValue().contains(normalizedLocation)) {
                return entry.getValue();
            }
        }
        return Collections.emptySet();
    }

    /**
     * Lấy tên chính thức của location (nếu có trong map)
     */
    public static String getCanonicalName(String location) {
        if (location == null) {
            return null;
        }

        String normalized = normalize(location);
        for (Map.Entry<String, Set<String>> entry : LOCATION_ALIASES.entrySet()) {
            if (entry.getValue().contains(normalized)) {
                // Tìm tên chính thức (key) - lấy key có dấu đầy đủ nhất
                return entry.getKey();
            }
        }

        // Không tìm thấy trong map, trả về location gốc (đã trim)
        return location.trim();
    }
}

