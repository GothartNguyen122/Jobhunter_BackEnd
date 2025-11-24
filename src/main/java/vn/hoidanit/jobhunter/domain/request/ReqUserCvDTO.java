package vn.hoidanit.jobhunter.domain.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqUserCvDTO {
    private String title;
    private String pdfUrl;
    private Boolean defaultCv;
}



