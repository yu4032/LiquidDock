package com.hellovoid.liquiddock;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Pure-Java loader and matcher for declarative widget-background rules. */
final class WidgetBackgroundRuleEngine {
    private static final String BUNDLED_RESOURCE = "widget_background_rules.xml";
    private static final String ACCESS_EXTERNAL_DTD =
            "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_SCHEMA =
            "http://javax.xml.XMLConstants/property/accessExternalSchema";
    private static final WidgetBackgroundRuleEngine EMPTY =
            new WidgetBackgroundRuleEngine(List.of());

    private final List<WidgetBackgroundRule> rules;

    private WidgetBackgroundRuleEngine(List<WidgetBackgroundRule> rules) {
        this.rules = List.copyOf(rules);
    }

    static WidgetBackgroundRuleEngine loadBundled() {
        ClassLoader loader = WidgetBackgroundRuleEngine.class.getClassLoader();
        if (loader == null) return EMPTY;
        try (InputStream input = loader.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (input == null) return EMPTY;
            return parse(input);
        } catch (Throwable ignored) {
            return EMPTY;
        }
    }

    static WidgetBackgroundRuleEngine parse(InputStream input) {
        if (input == null) return EMPTY;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            try { factory.setXIncludeAware(false); } catch (Throwable ignored) {}
            safeFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            safeFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            safeFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            try { factory.setAttribute(ACCESS_EXTERNAL_DTD, ""); }
            catch (Throwable ignored) {}
            try { factory.setAttribute(ACCESS_EXTERNAL_SCHEMA, ""); }
            catch (Throwable ignored) {}

            Document document = factory.newDocumentBuilder().parse(input);
            Element root = document.getDocumentElement();
            if (root == null || !"widget-background-rules".equals(root.getTagName())) return EMPTY;

            List<WidgetBackgroundRule> parsed = new ArrayList<>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (!(node instanceof Element)) continue;
                Element element = (Element) node;
                if (!"rule".equals(element.getTagName())) continue;
                WidgetBackgroundRule rule = parseRule(element);
                if (rule != null) parsed.add(rule);
            }
            return new WidgetBackgroundRuleEngine(parsed);
        } catch (Throwable ignored) {
            return EMPTY;
        }
    }

    WidgetBackgroundRule match(WidgetBackgroundIdentity identity) {
        WidgetBackgroundRule best = null;
        int bestScore = Integer.MIN_VALUE;
        for (WidgetBackgroundRule rule : rules) {
            if (!rule.matches(identity)) continue;
            int score = rule.specificity();
            if (best == null || score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        return best;
    }

    private static WidgetBackgroundRule parseRule(Element element) {
        String id = stringAttribute(element, "id");
        if (id == null) return null;
        List<String> hideElements = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            Element action = (Element) node;
            if (!"hide-element".equals(action.getTagName())) continue;
            String name = stringAttribute(action, "name");
            if (name != null) hideElements.add(name);
        }
        try {
            return new WidgetBackgroundRule(
                    id,
                    stringAttribute(element, "type"),
                    stringAttribute(element, "productId"),
                    stringAttribute(element, "appPackage"),
                    intAttribute(element, "spanX"),
                    intAttribute(element, "spanY"),
                    intAttribute(element, "configSpanX"),
                    intAttribute(element, "configSpanY"),
                    hideElements);
        } catch (NumberFormatException invalidConstraint) {
            // A malformed identity constraint must never silently become a wildcard and broaden
            // the destructive match. Reject only the invalid rule; keep other valid rules usable.
            return null;
        }
    }

    private static String stringAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer intAttribute(Element element, String name) {
        String value = stringAttribute(element, name);
        if (value == null) return null;
        return Integer.valueOf(value);
    }

    private static void safeFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try { factory.setFeature(name, value); }
        catch (Throwable ignored) {}
    }
}
