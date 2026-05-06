package com.agentcenter.bridge.infrastructure.persistence.entity;

public class RuntimeSkillVersionEntity {
    private String id;
    private String skillId;
    private String versionNo;
    private String packageChecksum;
    private Long packageSize;
    private Integer fileCount;
    private String installedRelativePath;
    private String manifestJson;
    private String skillMdSummary;
    private String status;
    private String createdBy;
    private String createdAt;

    public RuntimeSkillVersionEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getPackageChecksum() { return packageChecksum; }
    public void setPackageChecksum(String packageChecksum) { this.packageChecksum = packageChecksum; }
    public Long getPackageSize() { return packageSize; }
    public void setPackageSize(Long packageSize) { this.packageSize = packageSize; }
    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
    public String getInstalledRelativePath() { return installedRelativePath; }
    public void setInstalledRelativePath(String installedRelativePath) { this.installedRelativePath = installedRelativePath; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
    public String getSkillMdSummary() { return skillMdSummary; }
    public void setSkillMdSummary(String skillMdSummary) { this.skillMdSummary = skillMdSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
