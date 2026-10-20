package com.iras;

import java.io.Serializable;

public record AccountPrincipal(long id, String account, String role) implements Serializable {}