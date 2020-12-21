package com.jsonex.cliarg;

import com.jsonex.core.annotation.Description;
import com.jsonex.core.annotation.Examples;
import com.jsonex.core.annotation.Name;
import com.jsonex.core.annotation.Summary;
import com.jsonex.core.type.Nullable;
import com.jsonex.core.util.Assert;
import com.jsonex.core.util.BeanProperty;
import com.jsonex.core.util.ClassUtil;
import lombok.Data;
import lombok.SneakyThrows;

import java.util.*;

import static com.jsonex.core.util.LangUtil.doIf;
import static com.jsonex.core.util.LangUtil.doIfNotNull;
import static com.jsonex.core.util.ListUtil.first;
import static com.jsonex.core.util.ListUtil.setAt;
import static com.jsonex.core.util.StringUtil.noNull;

@Data
public class CLISpec<T> {
  final Class<T> cls;
  final T defVal;
  String name;
  @Nullable String summary;
  @Nullable String description;
  @Nullable String[] examples;

  int firstOptionalIndex = Integer.MAX_VALUE;
//  Map<String, Param> nameParamMap = new HashMap<>();
//  Map<String, Param> shortNameParamMap = new HashMap<>();
  List<Param> optionParams = new ArrayList<>();
  List<Param> indexedParams = new ArrayList<>();
  Set<String> requiredParams = new HashSet<>();

  @SneakyThrows
  public CLISpec(Class<T> cliCls) {
    this.cls = cliCls;
    this.defVal = this.cls.newInstance();
    init();
  }

  private void init() {
    name = cls.getSimpleName();
    doIfNotNull(cls.getAnnotation(Name.class), a -> name = a.value());
    doIfNotNull(cls.getAnnotation(Description.class), a -> description = a.value());
    doIfNotNull(cls.getAnnotation(Summary.class), a -> summary = a.value());
    doIfNotNull(cls.getAnnotation(Examples.class), a -> examples = a.value());

    Map<String, BeanProperty> propertyMap = ClassUtil.getProperties(cls);
    for(BeanProperty prop : propertyMap.values()) {
      if (prop.isImmutable(false))
        continue;
      Param param = new Param(prop, defVal);
      doIf(param.isRequired(), () -> requiredParams.add(param.name));

      if (param.index != null) {
        setAt(indexedParams, param.index, param);
        if (!param.required)
          firstOptionalIndex = Math.min(firstOptionalIndex, param.index);
        else {
          Assert.isTrue(param.index < firstOptionalIndex,
              "Required index argument can't be after Non-Required argument: firstOptionalIndex:"
                  + firstOptionalIndex + "; param: " + param);
        }
      } else
        optionParams.add(param);
    };
  }

  public Optional<Param> getOptionParamByName(String name) {
    return first(optionParams, p -> p.name.equals(name) || p.shortName.equals(name));
  }

  public String printUsage() {
    StringBuilder sb = new StringBuilder("NAME: " + name);
    doIfNotNull(summary, s -> sb.append("\nSUMMARY: " + s));
    doIfNotNull(description, s -> sb.append("\nDESCRIPTION\n  " + s));
    sb.append("\nUSAGE\n  " + getUsage());
    if (examples != null && examples.length > 0) {
      sb.append("\nEXAMPLES");
      for (String ex : examples)
        sb.append("\n  " + ex);
    }

    sb.append("\nARGUMENTS / OPTIONS");
    for (Param p : indexedParams) {
      sb.append("\n  <" + p.name + ">:  " + noNull(p.description));
    }

    for (Param p : optionParams) {
      if (p.index != null)
        continue;
      sb.append("\n  ");
      doIfNotNull(p.shortName, (n) -> sb.append("-" + n + ", "));
      sb.append("--" + p.name);
      sb.append(":  " + noNull(p.description));
    }
    return sb.toString();
  }

  public String getUsage() {
    StringBuilder sb = new StringBuilder(name);
    for (Param param : optionParams)
      sb.append(param.getUsage());

    for (Param param : indexedParams)
      sb.append(param.getUsage());

    return sb.toString();
  }

  @SneakyThrows
  public T createDefaultInstance() {
    return cls.newInstance();
  }

  public CLIParser<T> parse(String[] args, int argIndex) {
    return new CLIParser<T>(this, args, argIndex).parse();
  }
}
