package cn.lineai.data.api;

import java.util.List;

/** Append-only failure log with a bounded size. */
public interface ErrorLogStore {

    int MAX_ENTRIES = 200;

    List<ErrorLog> list();

    void record(String title, String subtitle, String detail);

    void clear();
}
