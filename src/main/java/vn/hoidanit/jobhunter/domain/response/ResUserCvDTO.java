package vn.hoidanit.jobhunter.domain.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResUserCvDTO {
    private long id;
    private String title;
    private String pdfUrl;
    private boolean defaultCv;
    private Instant createdAt;
    private Instant updatedAt;
}



