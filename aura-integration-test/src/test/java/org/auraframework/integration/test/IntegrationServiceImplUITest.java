/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.integration.test;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.auraframework.def.ComponentDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.HelperDef;
import org.auraframework.def.ProviderDef;
import org.auraframework.def.StyleDef;
import org.auraframework.integration.test.util.WebDriverTestCase;
import org.auraframework.test.util.AuraTestCase;
import org.auraframework.test.util.AuraTestingMarkupUtil;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.json.JsonEncoder;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * UI test for usage of Integration Service.
 */
@ThreadHostileTest("Tests modify if locker service is enabled")
public class IntegrationServiceImplUITest extends WebDriverTestCase {

    // defaultStubCmp : act as a container inject other components into, async is FALSE
    DefDescriptor<ComponentDef> defaultStubCmp;
    // use as id for cssSelector, where we put the injected component
    String defaultPlaceholderID = "placeholder";
    // aura id of injected component
    String defaultLocalId = "injectedComponent";

    @Inject
    private AuraTestingMarkupUtil tmu;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        getMockConfigAdapter().setLockerServiceEnabled(false);

        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                true, true, true);

        defaultStubCmp = addSourceAutoCleanup(ComponentDef.class, integrationStubMarkup);
    }


    /**
     * Verify using IntegrationService to inject a simple component with a Java model, Javascript Controller and Java
     * Controller.
     */
    // Click is unsupported in these touch based platforms
    @ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE})
    @Test
    public void testSimpleComponentWithModelAndController() throws Exception {
        verifySimpleComponentWithModelControllerHelperandProvider(defaultStubCmp);
    }

    /**
     * Verify using IntegrationService to inject a simple component with a Java model, Javascript Controller and Java
     * Controller. (ASYNC)
     */
    @ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE})
    @Test
    public void testSimpleComponentWithModelAndControllerAsync() throws Exception {
        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                true, true, true, true);
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(ComponentDef.class,integrationStubMarkup);

        verifySimpleComponentWithModelControllerHelperandProvider(stub);
    }


    private void verifySimpleComponentWithModelControllerHelperandProvider(DefDescriptor<ComponentDef> stub) throws Exception {
        DefDescriptor<ComponentDef> cmpToInject = setupSimpleComponentWithModelControllerHelperAndProvider();
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("strAttribute", "Oranges");
        String selectorForPlaceholder = String.format("div#%s", defaultPlaceholderID);

        openIntegrationStub(stub, cmpToInject, attributes);

        getAuraUITestingUtil().waitForElement("Injected component not found inside placeholder",
                By.cssSelector(selectorForPlaceholder + ">" + "div.wrapper"));
        WebElement attrValue = findDomElement(By.cssSelector("div.dataFromAttribute"));
        assertEquals("Failed to see data from model of injected component", "Oranges", attrValue.getText());
        WebElement modelValue = findDomElement(By.cssSelector("div.dataFromModel"));
        assertEquals("Failed to initilize attribute value of injected component", "firstThingDefault",
                modelValue.getText());

        WebElement button = findDomElement(By.cssSelector(".btnHandleClick"));
        button.click();
        getAuraUITestingUtil().waitForElementText(By.cssSelector("div.dataFromController"), "TestController", true);

        // Access injected component through ClientSide API
        assertTrue(getAuraUITestingUtil().getBooleanEval(String.format(
                "return window.$A.getRoot().find('%s')!== undefined ;", defaultLocalId)));
        assertEquals(cmpToInject.toString(),
                (String) getAuraUITestingUtil().getEval(String.format(
                        "return window.$A.getRoot().find('%s').getDef().getDescriptor().toString()", defaultLocalId)));

        WebElement valueFromJsProvider = findDomElement(By.cssSelector("div.dataFromJSProvider"));
        assertEquals("Failed to see data from model of injected component",
                "ValueFromJsProvider[ValueFromHelper]", valueFromJsProvider.getText());

        WebElement valueFromJavaProvider = findDomElement(By.cssSelector("div.dataFromJavaProvider"));
        assertEquals("Failed to see data from model of injected component",
                "valueFromJavaProvider", valueFromJavaProvider.getText());

        WebElement buttonShowStyle = findDomElement(By.cssSelector(".btnShowStyle"));
        buttonShowStyle.click();
        getAuraUITestingUtil().waitForElementFunction(By.cssSelector("div.dataFromAttributeStyle"),
                element -> element.getText().startsWith("rgb(255, 255, 255)")||element.getText().startsWith("#fff")
        );
    }

    /*
     * this creates simple component with helper, JS controller, JS provider , Java provider and Java model in the
     * helper we have function return a string (returnAString) in the JS provider, we set attribute(valueFromJSProvider)
     * with the string we get from helper in the Java provider, we set attribute(valueFromJavaProvider) in the JS
     * controller, we handler click event , push text component to the body each it clicks
     */
    private DefDescriptor<ComponentDef> setupSimpleComponentWithModelControllerHelperAndProvider() {
        DefDescriptor<ComponentDef> cmpDesc = getAuraTestingUtil().createStringSourceDescriptor(null,
                ComponentDef.class, null);
        DefDescriptor<ControllerDef> jsControllerdesc = definitionService
                .getDefDescriptor(cmpDesc, DefDescriptor.JAVASCRIPT_PREFIX, ControllerDef.class);
        DefDescriptor<ProviderDef> jsProviderdesc = definitionService
                .getDefDescriptor(cmpDesc, DefDescriptor.JAVASCRIPT_PREFIX, ProviderDef.class);
        DefDescriptor<HelperDef> jsHelperdesc = definitionService
                .getDefDescriptor(cmpDesc,  DefDescriptor.JAVASCRIPT_PREFIX, HelperDef.class);
        DefDescriptor<StyleDef> CSSdesc = definitionService
                .getDefDescriptor(cmpDesc,  DefDescriptor.CSS_PREFIX, StyleDef.class);
        //fill in component to be injected
        String jsProviderName = jsProviderdesc.getQualifiedName();
        String systemAttributes = "model='java://org.auraframework.components.test.java.model.TestModel' "
                + "controller='java://org.auraframework.components.test.java.controller.TestController' "
                + "provider='" + jsProviderName
                + ",java://org.auraframework.impl.java.provider.TestComponnetConfigProviderAIS' ";
        String bodyMarkup = "<aura:attribute name='strAttribute' type='String' default='Apple'/> "
                + "<aura:attribute name='valueFromJSProvider' type='String' default='Empty'/> "
                + "<aura:attribute name='valueFromJavaProvider' type='String' default='Empty'/> "
                + "<aura:attribute name='dataFromAttributeStyle' type='String' default='Empty'/> "
                + "<ui:button aura:id='btnHandleClick' class='btnHandleClick' label='clickMe' press='{!c.handleClick}'/>"
                + "<ui:button aura:id='btnShowStyle' class='btnShowStyle' label='showStyle' press='{!c.showStyle}'/>"
                + "<div class='wrapper'>"
                + "<div class='dataFromAttribute' aura:id='dataFromAttribute'>{!v.strAttribute}</div> "
                + "<div class='dataFromModel' aura:id='dataFromModel'>{!m.firstThing}</div> "
                + "<div class='dataFromController' aura:id='dataFromController'></div>"
                + "<div class='dataFromJSProvider' aura:id='dataFromJSProvider'>{!v.valueFromJSProvider}</div>"
                + "<div class='dataFromJavaProvider' aura:id='dataFromJavaProvider'>{!v.valueFromJavaProvider}</div>"
                + "<div class='dataFromAttributeStyle' aura:id='dataFromAttributeStyle'>{!v.dataFromAttributeStyle}</div> "
                + "</div>";
        addSourceAutoCleanup(cmpDesc, String.format(baseComponentTag, systemAttributes,bodyMarkup));
        //fill in js controller
        addSourceAutoCleanup(jsControllerdesc,
        "{"
            + "  handleClick:function(cmp){"
            + "    var valueFromHelper = cmp.getDef().getHelper().returnAString();"
            + "    var a = cmp.get('c.getString');"
            + "    a.setCallback(cmp,function(a){ "
            + "      $A.createComponent("
            + "        'aura:text',"
            + "        { value : a.getReturnValue() },"
            + "        function(newCmp){"
            + "          var body = cmp.find('dataFromController').get('v.body');"
            + "          body.push(newCmp);"
            + "          cmp.find('dataFromController').set('v.body', body);"
            + "        }"
            + "      );"
            + "    });"
            + "    $A.enqueueAction(a);"
            + "  },"
            + "  showStyle: function(cmp) {"
            + "    var dfaElement = cmp.find('dataFromAttribute').getElement(); "
            + "    cmp.set('v.dataFromAttributeStyle','call getCSSProperty');"
            + "    var dfaStyle = $A.util.style.getCSSProperty(dfaElement,'color'); "
            + "    if(dfaStyle == undefined) { dfaStyle = 'get background return undefined!';} "
            + "    cmp.set('v.dataFromAttributeStyle',dfaStyle);"
            + "  }"
            + "}"
        );
        //fill in js provider
        addSourceAutoCleanup(jsProviderdesc,
          "{"
            + "  provide : function AisToInjectProvider(component) {"
            + "    var valueFromHelper = component.getDef().getHelper().returnAString();"
            + "    var returnvalue = 'ValueFromJsProvider['+valueFromHelper+']';"
            + "    return {"
            + "      attributes: {"
            + "        'valueFromJSProvider': returnvalue"
            + "      }"
            + "    };"
            + "  }"
            + "}"
        );
        //fill in helper
        addSourceAutoCleanup(jsHelperdesc,
            "{"
            + "  returnAString: function() {"
            + "    return 'ValueFromHelper';"
            + "  }"
            + "}"
        );
        //fill in CSS
        addSourceAutoCleanup(CSSdesc, ".THIS .dataFromAttribute { color: #fff; } ");

        return cmpDesc;
    }

    @Test
    public void testSimpleComponentWithExtension() throws Exception {
        verifySimpleComponentWithExtension(defaultStubCmp);
    }

    @Test
    public void testSimpleComponentWithExtensionAsync() throws Exception {
        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                true, true, true, true);
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(ComponentDef.class, integrationStubMarkup);

        verifySimpleComponentWithExtension(stub);
    }

    private void verifySimpleComponentWithExtension(DefDescriptor<ComponentDef> stub) throws Exception {
        DefDescriptor<ComponentDef> cmpToInject = setupSimpleComponentWithExtension();
        Map<String, Object> attributes = Maps.newHashMap();

        openIntegrationStub(stub, cmpToInject, attributes);

        WebElement attrValueInBaseCmp = findDomElement(By.cssSelector("div.attrInBaseCmp"));
        assertEquals("Expecting different attribute value in base cmp", "In BaseCmp : SimpleAttribute= We just Set it", attrValueInBaseCmp.getText());

        WebElement attrValueFromBaseCmp = findDomElement(By.cssSelector("div.attrFromBaseCmp"));
        assertEquals("Expecting different attribute value in extended cmp", "In BaseCmp : SimpleAttribute= We just Set it", attrValueFromBaseCmp.getText());

    }

    private DefDescriptor<ComponentDef> setupSimpleComponentWithExtension() {
        DefDescriptor<ComponentDef> baseCmpDesc =  setupSimpleComponentToExtend();
        DefDescriptor<ComponentDef> cmpDesc = getAuraTestingUtil().createStringSourceDescriptor(null,
                ComponentDef.class, null);
        String systemAttributes=String.format("extends='%s:%s' ", baseCmpDesc.getNamespace(),baseCmpDesc.getName());
        String bodyMarkup = "<aura:set attribute='SimpleAttribute'> We just Set it </aura:set> "
                + "<div class='attrFromBaseCmp'>In BaseCmp : SimpleAttribute={!v.SimpleAttribute}  </div>";
        addSourceAutoCleanup(cmpDesc, String.format(baseComponentTag, systemAttributes,bodyMarkup));

        return cmpDesc;
    }

    private DefDescriptor<ComponentDef> setupSimpleComponentToExtend() {
        DefDescriptor<ComponentDef> cmpDesc = getAuraTestingUtil().createStringSourceDescriptor(null,
                ComponentDef.class, null);
        String systemAttributes="extensible='true' ";
        String bodyMarkup =
        "<aura:attribute name='SimpleAttribute' type='String' default='DefaultStringFromBaseCmp'/>"
        + "{!v.body}"
        + "<div class='attrInBaseCmp'>In BaseCmp : SimpleAttribute={!v.SimpleAttribute} </div>";
        addSourceAutoCleanup(cmpDesc, String.format(baseComponentTag, systemAttributes,bodyMarkup));

        return cmpDesc;
    }

    /**
     * Verify use of integration service to inject a component and initialize various types of attributes.
     */
    @Test
    public void testAttributesInitialization() throws Exception {
        verifyAttributesInitialization(defaultStubCmp, false);
    }

    /**
     * Verify use of integration service to inject a component and initialize various types of attributes. (ASYNC)
     */
    @Test
    public void testAttributesInitializationAsync() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                        true, true, true, true)
                );

        verifyAttributesInitialization(stub, true);
    }

    private void verifyAttributesInitialization(DefDescriptor<ComponentDef> stub, boolean async) throws Exception {
        String attributeMarkup = tmu.getCommonAttributeMarkup(true, true, true, false)
                + tmu.getCommonAttributeListMarkup(true, true, false, false, false);
        String attributeWithDefaultsMarkup =
                tmu.getCommonAttributeWithDefaultMarkup(true, true, true, false,
                        "'Apple'", "'true'", "'Banana'", "") +
                        tmu.getCommonAttributeListWithDefaultMarkup(true, true, false, false, false,
                                "'Apple,Orange'", "'Melon,Berry,Grapes'", "", "", "");

        DefDescriptor<ComponentDef> cmpToInject = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "", attributeMarkup + attributeWithDefaultsMarkup));

        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("strAttr", "Oranges");
        attributes.put("booleanAttr", false);
        List<String> strList = Lists.newArrayList("Pear", "Melon");
        attributes.put("strList", strList);
        List<String> stringList = Lists.newArrayList("Persimon", "Kiwi");
        attributes.put("stringList", stringList.toArray());
        attributes.put("objAttr", "Object");

        String attributeEvalScript = "return window.$A.getRoot().find('%s').get('v.%s');";

        openIntegrationStub(stub, cmpToInject, attributes);

        String script = String.format("return window.$A.getRoot().find('%s')!== undefined ;", defaultLocalId);
        if(async) {
            waitForCondition(script);
        } else {
            // Access injected component through ClientSide API
            assertTrue("Failed to find injected component.", getAuraUITestingUtil().getBooleanEval(script));
        }

        // Provided attributes
        assertEquals("Oranges",
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "strAttr")));
        assertFalse(getAuraUITestingUtil().getBooleanEval(String.format(attributeEvalScript, defaultLocalId, "booleanAttr")));
        assertEquals("Object", getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "objAttr")));
        assertEquals(strList, getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "strList")));
        assertEquals(stringList,
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "stringList")));

        // Attributes with Default values
        assertEquals("Apple",
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "strAttrDefault")));
        assertTrue(getAuraUITestingUtil().getBooleanEval(String.format(attributeEvalScript, defaultLocalId,
                "booleanAttrDefault")));
        assertEquals("Banana",
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "objAttrDefault")));
        assertEquals(Lists.newArrayList("Apple", "Orange"),
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "strListDefault")));
        assertEquals(Lists.newArrayList("Melon", "Berry", "Grapes"),
                getAuraUITestingUtil().getEval(String.format(attributeEvalScript, defaultLocalId, "stringListDefault")));

    }

    /**
     * Verify we can fire custom event from injected component, then handle it in top application level Note in
     * RendererWithExtendedApp, we are not using the aura:integrationServiceApp, instead we are using its extension
     * (ASYNC) Click is unsupported in these touch based platforms
     */
    @ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE })
    @Test
    public void testExtendedAppWithRegisteredEventsAsync() throws Exception {
        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererWithExtendedApp",
                true, true, true, true);
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(ComponentDef.class, integrationStubMarkup);
        verifyComponentWithRegisteredEvents(stub, true);
    }

    /**
     * Verify use of integration service to inject a component and initialize events with javascript function handlers.
     */
    // Click is unsupported in these touch based platforms
    @ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE })
    @Test
    public void testComponentWithRegisteredEvents() throws Exception {
        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForAISWithCustomJScript",
                true, true, true);
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class, integrationStubMarkup);
        verifyComponentWithRegisteredEvents(stub, false);
    }

    /**
     * Verify use of integration service to inject a component and initialize events with javascript function handlers.
     * (ASYNC)
     */
    @ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE })
    @Test
    public void testComponentWithRegisteredEventsAsync() throws Exception {
        String integrationStubMarkup = getIntegrationStubMarkup(
                "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForAISWithCustomJScript",
                true, true, true, true);
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class, integrationStubMarkup);
        verifyComponentWithRegisteredEvents(stub, false);
    }

    /*
     * this function create component to inject, it registers three custom events(two component one application), fire
     * them in its controller. for container component to catch these events, one needs to add key->value
     * pair(eventName-->handlerFunctionName), then in container's markup, find a way to inject the hander functions
     * within <script>...</script>
     */
    private void verifyComponentWithRegisteredEvents(DefDescriptor<ComponentDef> stub, boolean checkAppEvt)
            throws Exception {
        // create injected component with custom events.
        String bodyMarkup = "<aura:attribute name='attr' type='String' default='Oranges'/> "
                + "<aura:registerevent name='press' type='ui:press'/>"
                + "<aura:registerevent name='change' type='ui:change'/>"
                + "<aura:registerevent name='appEvtFromInjectedCmp' type='handleEventTest:applicationEvent'/>"
                + "<div class='dataFromAttribute' aura:id='dataFromAttribute'>{!v.attr}</div>"
                + "<div class='click_t' onclick='{!c.clickHndlr}'>Click Me</div>"
                + "<input class='change_t' onchange='{!c.changeHndlr}' type='text'/>"
                + "<div class='click2_t' onclick='{!c.click2Hndlr}'>Click Me2</div>"
                + "<div class='click3_t' onclick='{!c.click3Hndlr}'>Click Me3</div>";
        DefDescriptor<ComponentDef> cmpToInject = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "", bodyMarkup));
        DefDescriptor<ControllerDef> jsControllerdesc = definitionService
                .getDefDescriptor(
                        String.format("%s://%s.%s", DefDescriptor.JAVASCRIPT_PREFIX, cmpToInject.getNamespace(),
                                cmpToInject.getName()), ControllerDef.class
                );
        // create controller to fire events
        addSourceAutoCleanup(jsControllerdesc,
                "{"
                + "   clickHndlr: function(cmp, evt){var e = cmp.getEvent('press');e.setParams({'domEvent': evt});e.fire();},"
                + "   changeHndlr: function(cmp, evt){var e = cmp.getEvent('change');e.fire();},"
                + "   click2Hndlr: function(cmp, evt){var e = cmp.getEvent('appEvtFromInjectedCmp'); e.fire();},"
                + "   click3Hndlr: function(cmp, evt){var e = $A.getEvt('handleEventTest:applicationEvent'); e.setParams({'strAttr': 'event fired from click3Hndlr'}); e.fire();}"
                + "}"
        );
        // associate custom event name to handler function name using attribute map
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("attr", "Apples");
        attributes.put("press", "clickHandler__t");
        attributes.put("change", "changeHandler__t");
        attributes.put("appEvtFromInjectedCmp", "click2Handler__t");

        openIntegrationStub(stub, cmpToInject, attributes);

        WebElement attrValue = findDomElement(By.cssSelector("div.dataFromAttribute"));
        assertEquals("Failed to see data from model of injected component", "Apples", attrValue.getText());

        // component event 1
        WebElement clickNode = findDomElement(By.cssSelector("div.click_t"));
        clickNode.click();
        assertTrue("expect _clickHandlerCalled to be true",
                getAuraUITestingUtil().getBooleanEval("return document._clickHandlerCalled"));
        assertEquals("press", getAuraUITestingUtil().getEval("return document.__clickEvent.getName()"));

        WebElement textNode = findDomElement(By.cssSelector("input.change_t"));
        textNode.sendKeys("YeeHa!");
        clickNode.click(); // This will take the focus out of input element and trigger the onchange handler
        waitForCondition("return !!document._changeHandlerCalled");
        assertEquals("Custom JS Code", getAuraUITestingUtil().getEval("return document._changeHandlerCalled"));
        assertEquals("change", getAuraUITestingUtil().getEval("return document.__changeEvent.getName()"));

        // component event 2
        WebElement clickNode2 = findDomElement(By.cssSelector("div.click2_t"));
        clickNode2.click();
        assertTrue("clickHandler2Called should be true after container cmp handle the evt from injected cmp",
                getAuraUITestingUtil().getBooleanEval("return document._click2HandlerCalled"));
        assertEquals("didn't get expect event name from Click Me2",
                "appEvtFromInjectedCmp",
                getAuraUITestingUtil().getEval("return document.__click2Event.getName()"));

        // application event
        if (checkAppEvt) {
            WebElement clickNode3 = findDomElement(By.cssSelector("div.click3_t"));
            clickNode3.click();
            assertTrue("clickHandler3Called should be true after container cmp handle the evt from injected cmp",
                    getAuraUITestingUtil().getBooleanEval("return document._click3HandlerCalled"));
            assertEquals("didn't get expect event param from Click Me3",
                    "event fired from click3Hndlr",
                    getAuraUITestingUtil().getEval("return document.__click3EventParam"));
        }

    }

    /**
     * Verify use of integration service to inject a component and initialize a Aura.Component[] type attribute.
     *
     * @throws Exception
     */
    @Ignore("W-1498384")
    @Test
    public void testComponentArrayAsAttribute() throws Exception {
        String attributeMarkup = "<aura:attribute name='cmps' type='Aura.Component[]'/>{!v.cmps}";
        String attributeWithDefaultsMarkup = "<aura:attribute name='cmpsDefault' type='Aura.Component[]'>"
                + "<div class='divDefault'>Div as default</div>"
                + "<span class='spanDefault'>Span component as default</span>" + "</aura:attribute>{!v.cmpsDefault}";
        DefDescriptor<ComponentDef> cmpToInject = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "", attributeMarkup + attributeWithDefaultsMarkup));

        DefDescriptor<ComponentDef> customStub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererToInjectComponentAsAttributes",
                        false, false,
                        false));

        openIntegrationStub(customStub, cmpToInject, null);

        // Access injected component through ClientSide API
        assertTrue("Failed to locate injected component on page.",
                getAuraUITestingUtil().getBooleanEval(String.format("return window.$A.getRoot().find('%s')!== undefined ;",
                        "localId")));

        // Default values for attributes of type Aura.Component[]
        WebElement devDefault = findDomElement(By.cssSelector("div.divDefault"));
        assertEquals("Failed to initializing attribute of type Aura.Component[]", "Div as default",
                devDefault.getText());
        WebElement spanDefault = findDomElement(By.cssSelector("span.spanDefault"));
        assertEquals("Failed to initializing attribute of type Aura.Component[]", "Span component as default",
                spanDefault.getText());

        // Attribute value passing for Aura.Component[] type attributes
        assertTrue("Failed passing value to Aura.Component[] type attributes",
                isElementPresent(By.xpath("//div[@id='placeholder' and contains(.,'Water Melon')]")));
        assertTrue(isElementPresent(By.xpath("//div[@id='placeholder' and contains(.,'Grape Fruit')]")));
    }

    /**
     * Verify the behavior of injectComponent when the placeholder specified is missing.
     */
    @Test
    public void testMissingPlaceholder() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererToInjectCmpInNonExistingPlaceholder",
                        true, true, true)
                );
        verifyMissingPlaceholder(stub, "locatorDomId");
    }

    /**
     * Verify the behavior of injectComponent when the placeholder specified is missing. (ASYNC)
     */
    @Test
    public void testMissingPlaceholderAsync() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererToInjectCmpInNonExistingPlaceholder",
                        true, true, true, true)
                );
        verifyMissingPlaceholder(stub, "locator");
    }

    private void verifyMissingPlaceholder(DefDescriptor<ComponentDef> stub, String locatorName) throws Exception {
        DefDescriptor<ComponentDef> cmpToInject = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "", ""));

        String badPlaceholder = "fooBared";
        String expectedErrorMessage = String.format(
                "Invalid " + locatorName + " specified - no element found in the DOM with id=%s", badPlaceholder);

        openIntegrationStub(stub, cmpToInject, null, badPlaceholder);

        boolean isErrorPresent = findDomElement(By.cssSelector("span[class='uiOutputText']")).getText().contains(
                expectedErrorMessage);
        assertTrue("IntegrationService failed to display error message when invalid locator was specified",
                isErrorPresent);
    }

    /**
     * Verify that specifying localId(aura:id) for an injected component is allowed.
     */
    @Test
    public void testMissingLocalId() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererToInjectCmpWithNullLocalId",
                        true, true, false)
                );

        verifyMissingLocalId(stub);
    }

    /**
     * Verify that specifying localId(aura:id) for an injected component is allowed. (ASYNC)
     */
    @Test
    public void testMissingLocalIdAsync() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererToInjectCmpWithNullLocalId",
                        true, true, false, true)
                );

        verifyMissingLocalId(stub);
    }

    private void verifyMissingLocalId(DefDescriptor<ComponentDef> stub) throws Exception {
        DefDescriptor<ComponentDef> cmpToInject = definitionService.getDefDescriptor("aura:text",
                ComponentDef.class);
        Map<String, Object> attributes = Maps.newHashMap();
        attributes.put("value", "No Local Id");

        openIntegrationStub(stub, cmpToInject, attributes, null);
        assertTrue("IntegrationService failed to inject component when no localId was specified",
                isElementPresent(By.xpath("//div[@id='placeholder' and contains(.,'No Local Id')]")));

    }

    /**
     * Verify that exceptions that happen during component instance creation are surfaced on the page.
     */
    @Test
    public void testExceptionDuringComponentInitialization() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                        true, true, true)
                );
        verifyExceptionDuringComponentInitialization(stub);
    }

    /**
     * Verify that exceptions that happen during component instance creation are surfaced on the page. (ASYNC)
     */
    @Test
    public void testExceptionDuringComponentInitializationAsync() throws Exception {
        DefDescriptor<ComponentDef> stub = addSourceAutoCleanup(
                ComponentDef.class,
                getIntegrationStubMarkup(
                        "java://org.auraframework.impl.renderer.sampleJavaRenderers.RendererForTestingIntegrationService",
                        true, true, true, true)
                );
        verifyExceptionDuringComponentInitialization(stub);
    }

    private void verifyExceptionDuringComponentInitialization(DefDescriptor<ComponentDef> stub) throws Exception {
        DefDescriptor<ComponentDef> cmpWithReqAttr = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "",
                        "<aura:attribute name='reqAttr' required='true' type='String'/>"));
        Map<String, Object> attributes = Maps.newHashMap();
        String expectedErrorMessage = "is missing required attribute 'reqAttr'";

        openIntegrationStub(stub, cmpWithReqAttr, attributes);

        boolean isErrorPresent = findDomElement(By.cssSelector("span[class='uiOutputText']")).getText().contains(
                expectedErrorMessage);
        assertTrue("IntegrationService failed to display error message", isErrorPresent);
    }

    /**
     * Verify that using multiple integration objects on page does not duplicate Aura Framework injection.
     */
    @Ignore("W-1498404")
    @Test
    public void testMultipleIntegrationObjectsOnSamePage() throws Exception {
        DefDescriptor<ComponentDef> cmpToInject = setupSimpleComponentWithModelControllerHelperAndProvider();
        Map<String, Object> attributes = Maps.newHashMap();
        String facets[] = new String[] { "Panda", "Tiger" };
        String facetMarkup = "";
        for (String facet : facets) {
            attributes.put("strAttribute", facet);
            facetMarkup = facetMarkup
                    + String.format("<%s:%s desc='%s:%s' attrMap='%s' placeholder='%s' localId='%s'/>",
                            defaultStubCmp.getNamespace(),
                            defaultStubCmp.getName(), cmpToInject.getNamespace(), cmpToInject.getName(),
                            JsonEncoder.serialize(attributes), "Animal" + facet, facet);

        }

        DefDescriptor<ComponentDef> customStubCmp = addSourceAutoCleanup(ComponentDef.class,
                String.format(AuraTestCase.baseComponentTag, "render='server'", facetMarkup));

        openIntegrationStub(customStubCmp, cmpToInject, null, null);
        assertTrue(isElementPresent(By.xpath("//div//script[contains(@src,'aura_dev.js')]")));
        assertEquals("Framework loaded multiple times", 1,
                getDriver().findElements(By.xpath("//div//script[contains(@src,'aura_dev.js')]")).size());

        WebElement attrValue = findDomElement(By.cssSelector("#AnimalPanda div.dataFromAttribute"));
        assertEquals("Failed to locate first component injected.", "Panda", attrValue.getText());

        attrValue = findDomElement(By.cssSelector("#AnimalTiger div.dataFromAttribute"));
        assertEquals("Failed to locate second component injected.", "Tiger", attrValue.getText());

    }

    @Ignore("W-1498404 - Based on the fix, this test case should be implemented")
    @Test
    public void testMultipleIntegrationObjectsOnSamePageWithDifferentModes() throws Exception {

    }

    /**
     * HistoryService is not initialized for a page which is using Integration service. This test verifies that calling
     * HistoryService APIs don't cause Javascript Errors on the page. HistoryService initialization takes care of
     * attaching a event handler for # changes in the URL. In case of integration service, this initialization is
     * skipped. So changing url # should not fire aura:locationChange event
     */
    // History Service is not supported in IE7 or IE8
    @ExcludeBrowsers({ BrowserType.IE8 })
    @Test
    public void testHistoryServiceAPIs() throws Exception {
        String expectedTxt = "";
        openIntegrationStub(
                definitionService.getDefDescriptor("integrationService:noHistoryService", ComponentDef.class),
                null);
        String initialUrl = getDriver().getCurrentUrl();
        // open("/integrationService/noHistoryService.cmp");
        assertEquals("At page initialization, aura:locationChange event should not be fired.", expectedTxt,
                getText(By.cssSelector("div.testDiv")));

        // historyService.set() to a new location - W-1506261
        getAuraUITestingUtil().getEval("$A.historyService.set('forward')");
        String url = (String) getAuraUITestingUtil().getEval("return window.location.href");
        assertTrue("Failed to change window location using set()", url.endsWith("#forward"));

        // historyService.get()
        assertEquals("get() failed to retrieve expected token",
                "forward", getAuraUITestingUtil().getEval("return $A.historyService.get().token"));

        // historyService.back()
        getAuraUITestingUtil().getEval("$A.historyService.back()");
        assertEquals("Failed to revert back to previous URL", initialUrl, getDriver().getCurrentUrl());
        assertEquals("History service failed to go back",
                "", getAuraUITestingUtil().getEval("return $A.historyService.get().token"));

        // historyService.forward()
        getAuraUITestingUtil().getEval("$A.historyService.forward()");
        assertEquals("History service does provided unexpected # token",
                "forward", getAuraUITestingUtil().getEval("return $A.historyService.get().token"));
        assertTrue("Window location does not end with expected #", getDriver().getCurrentUrl().endsWith("#forward"));

        // Manually firing locationChange event
        expectedTxt = "Location Change fired:1";
        getAuraUITestingUtil().getEval("$A.eventService.newEvent('aura:locationChange').fire()");
        assertEquals("Manully firing locationChange event failed",
                expectedTxt, getText(By.cssSelector("div.testDiv")));
    }

    private String getIntegrationStubMarkup(String javaRenderer, Boolean attributeMap, Boolean placeHolder,
            Boolean localId) {
        return this.getIntegrationStubMarkup(javaRenderer, attributeMap, placeHolder, localId, false);
    }

    /**
     * Utility method to obtain the required markup of the integration stub component.
     */
    private String getIntegrationStubMarkup(String javaRenderer, Boolean attributeMap, Boolean placeHolder,
            Boolean localId, Boolean useAsync) {
        String stubMarkup = String.format(
                "<aura:component render='server' renderer='%s'>" +
                "    <aura:attribute name='desc' type='String'/>" +
                "    %s %s %s %s" +
                "</aura:component>",
                javaRenderer,
                attributeMap ? "<aura:attribute name='attrMap' type='Map'/>" : "",
                placeHolder ? String.format("<aura:attribute name='placeholder' type='String' default='%s'/>",
                        defaultPlaceholderID) : "",
                localId ? String.format("<aura:attribute name='localId' type='String' default='%s'/>", defaultLocalId)
                        : "",
                String.format("<aura:attribute name='useAsync' type='Boolean' default='%s'/>", useAsync));
        return stubMarkup;
    }

    /**
     * Pass the descriptor and attributes map of the component to be injected to a Stub component. Open the stub
     * component using webdriver.
     */
    private void openIntegrationStub(DefDescriptor<ComponentDef> stub, DefDescriptor<ComponentDef> toInject,
            Map<String, Object> attributeMap, String placeholder)
            throws Exception {
        String url = String.format("/%s/%s.cmp", stub.getNamespace(), stub.getName());
        url = url + "?desc=" + String.format("%s:%s", toInject.getNamespace(), toInject.getName());

        if (attributeMap != null) {
            url = url + "&" + "attrMap=" + AuraTextUtil.urlencode(JsonEncoder.serialize(attributeMap));
        } else {
            url = url + "&" + "attrMap=" + AuraTextUtil.urlencode(JsonEncoder.serialize(Maps.newHashMap()));
        }

        if (placeholder != null) {
            url = url + "&placeholder=" + placeholder;
        }

        openNoAura(url);

        getAuraUITestingUtil().waitForDocumentReady();
        getAuraUITestingUtil().waitForAuraFrameworkReady(getAuraErrorsExpectedDuringInit());
    }

    private void openIntegrationStub(DefDescriptor<ComponentDef> stub, DefDescriptor<ComponentDef> toInject,
            Map<String, Object> attributeMap)
            throws Exception {
        openIntegrationStub(stub, toInject, attributeMap, null);
    }

    private void openIntegrationStub(DefDescriptor<ComponentDef> toInject, Map<String, Object> attributeMap)
            throws Exception {
        openIntegrationStub(defaultStubCmp, toInject, attributeMap, null);
    }
}
