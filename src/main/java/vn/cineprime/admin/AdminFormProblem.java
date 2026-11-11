package vn.cineprime.admin;

import java.util.List;

/** Machine-readable field location; messages remain Vietnamese and are never parsed by the UI. */
public class AdminFormProblem extends IllegalArgumentException {
    private final List<String> fields;
    public AdminFormProblem(String message,String... fields){super(message);this.fields=List.of(fields);}
    public List<String> fields(){return fields;}
}
