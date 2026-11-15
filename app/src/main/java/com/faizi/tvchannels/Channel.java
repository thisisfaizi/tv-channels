package com.faizi.tvchannels;

public class Channel {
    public final String name;
    public final String url;
    public final String category;

    public Channel(String name, String url, String category) {
        this.name = name;
        this.url = url;
        this.category = (category == null || category.trim().length() == 0)
                ? "Other" : category.trim();
    }

    @Override
    public String toString() {
        return name;
    }
}
