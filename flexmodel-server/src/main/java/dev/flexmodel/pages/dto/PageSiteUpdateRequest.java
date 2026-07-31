package dev.flexmodel.pages.dto;

import lombok.Data;

import java.util.List;

/**
 * @author cjbi
 */
@Data
public class PageSiteUpdateRequest {

  private List<String> customDomains;
}
