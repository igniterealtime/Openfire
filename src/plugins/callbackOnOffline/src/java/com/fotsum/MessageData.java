package com.fotsum;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public final class MessageData {
    private String token;
    private String from;
    private String to;
    private String body;

    MessageData(String token, String from, String to, String body) {
        this.token = token;
        this.from = from;
        this.to = to;
        this.body = body;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MessageData that = (MessageData) o;

        if (!token.equals(that.token)) return false;
        if (!from.equals(that.from)) return false;
        if (!to.equals(that.to)) return false;
        return body.equals(that.body);
    }

    @Override
    public int hashCode() {
        int result = token.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + to.hashCode();
        result = 31 * result + body.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MessageData{" +
                "token='" + token + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}
