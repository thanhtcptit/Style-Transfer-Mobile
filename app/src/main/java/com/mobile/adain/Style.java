package com.mobile.adain;

public class Style {
    private String name;
    private int styleImageId;
    private int isApply;

    public Style(String name, int styleImageId, int isApply) {
        this.name = name;
        this.styleImageId = styleImageId;
        this.isApply = isApply;
    }

    public int getIsApply() {
        return isApply;
    }

    public void setIsApply(int isApply) {
        this.isApply = isApply;
    }

    public String getName() {
        return name;
    }

    public int getStyleImageId() {
        return styleImageId;
    }
}
