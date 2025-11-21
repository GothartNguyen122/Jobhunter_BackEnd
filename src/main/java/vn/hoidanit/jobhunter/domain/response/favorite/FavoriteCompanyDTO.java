package vn.hoidanit.jobhunter.domain.response.favorite;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FavoriteCompanyDTO {
    private long favoriteId;
    private long companyId;
    private String name;
    private String address;
    private String logo;
    private Instant savedAt;
}



