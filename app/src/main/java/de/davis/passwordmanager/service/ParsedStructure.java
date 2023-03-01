package de.davis.passwordmanager.service;

import android.app.assist.AssistStructure;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.davis.passwordmanager.utils.AutofillSynonyms;

@RequiresApi(api = Build.VERSION_CODES.O)
public class ParsedStructure {

    private static boolean foundByHint;

    private AssistStructure.ViewNode usernameView;
    private AssistStructure.ViewNode passwordView;
    private AssistStructure.ViewNode webDomainView;

    private final List<AutofillId> additional = new ArrayList<>();

    public ParsedStructure() {}

    public AssistStructure.ViewNode getUsernameView() {
        return usernameView;
    }

    public AssistStructure.ViewNode getPasswordView() {
        return passwordView;
    }

    public AssistStructure.ViewNode getWebDomainView() {
        return webDomainView;
    }

    public String getWebDomain(){
        return webDomainView.getText() != null ? webDomainView.getText().toString() : webDomainView.getWebDomain();
    }

    public AutofillId[] getOptionals(){
        return additional.toArray(new AutofillId[0]);
    }

    public static ParsedStructure parse(AssistStructure assistStructure, Context context){
        List<AssistStructure.ViewNode> viewNodes = traverseStructure(assistStructure);

        ParsedStructure parsedStructure = new ParsedStructure();

        viewNodes.forEach(viewNode -> {
            boolean domain = tryWebDomain(parsedStructure, viewNode);
            if (tryByAutoFillHints(parsedStructure, viewNode))
                return;

            if (tryByIdEntry(parsedStructure, viewNode))
                return;

            if(tryByHint(parsedStructure, viewNode, context))
                return;

            if(domain)
                return;

            parsedStructure.additional.add(viewNode.getAutofillId());
        });

        return parsedStructure;
    }

    private static List<AssistStructure.ViewNode> traverseStructure(AssistStructure structure) {
        int nodes = structure.getWindowNodeCount();

        List<AssistStructure.ViewNode> nodesList = new ArrayList<>();
        for (int i = 0; i < nodes; i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode viewNode = windowNode.getRootViewNode();
            nodesList.addAll(traverseNode(viewNode));
        }
        return nodesList;
    }

    private static List<AssistStructure.ViewNode> traverseNode(AssistStructure.ViewNode viewNode) {
        List<AssistStructure.ViewNode> nodes = new ArrayList<>();
        if(viewNode.getWebDomain() != null || (viewNode.isEnabled() && viewNode.getClassName() != null && viewNode.getClassName().contains("EditText")))
            nodes.add(viewNode);

        for(int i = 0; i < viewNode.getChildCount(); i++) {
            AssistStructure.ViewNode childNode = viewNode.getChildAt(i);
            nodes.addAll(traverseNode(childNode));
        }
        return nodes;
    }

    private static boolean tryByAutoFillHints(ParsedStructure parsedStructure, AssistStructure.ViewNode viewNode){
        if(viewNode.getAutofillHints() == null || viewNode.getAutofillHints().length == 0)
            return false;

        boolean isUsernameOrEmail = Arrays.stream(viewNode.getAutofillHints()).anyMatch(hint -> hint.contains(View.AUTOFILL_HINT_USERNAME) || hint.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS));
        if(isUsernameOrEmail && parsedStructure.usernameView == null){
            parsedStructure.usernameView = viewNode;
            return true;
        }

        boolean isPassword = Arrays.stream(viewNode.getAutofillHints()).anyMatch(hint -> hint.contains(View.AUTOFILL_HINT_PASSWORD));
        if(isPassword && parsedStructure.passwordView == null){
            parsedStructure.passwordView = viewNode;
            return true;
        }

        return false;
    }

    private static boolean tryByIdEntry(ParsedStructure parsedStructure, AssistStructure.ViewNode viewNode){
        if(viewNode.getIdEntry() == null)
            return false;

        if(viewNode.getIdEntry().contains(View.AUTOFILL_HINT_USERNAME) && parsedStructure.usernameView == null){
            parsedStructure.usernameView = viewNode;
            return true;
        }

        if(viewNode.getIdEntry().contains(View.AUTOFILL_HINT_PASSWORD) && parsedStructure.passwordView == null){
            parsedStructure.passwordView = viewNode;
            return true;
        }

        return false;
    }

    private static synchronized boolean tryByHint(ParsedStructure parsedStructure, AssistStructure.ViewNode viewNode, Context context){
        if(viewNode.getHint() == null)
            return false;

        foundByHint = false;

        String hint = viewNode.getHint().toLowerCase().replace("-", "");

        AutofillSynonyms.getSynonyms(context).iterateLanguages(language -> {
            if(language.getUsernames().stream().anyMatch(hint::contains) && parsedStructure.usernameView == null){
                parsedStructure.usernameView = viewNode;
                foundByHint = true;
            }

            if(language.getPasswords().stream().anyMatch(hint::contains) && parsedStructure.passwordView == null){
                parsedStructure.passwordView = viewNode;
                foundByHint = true;
            }
        });

        return foundByHint;
    }

    private static boolean tryWebDomain(ParsedStructure parsedStructure, AssistStructure.ViewNode viewNode){
        if(viewNode.getWebDomain() == null)
            return false;

        parsedStructure.webDomainView = viewNode;

        return true;
    }

    public boolean isEmpty(){
        return usernameView == null && passwordView == null;
    }

    public AutofillId[] toIdArray() {
        if (isEmpty())
            return new AutofillId[0];

        if (usernameView != null && passwordView != null)
            return new AutofillId[]{usernameView.getAutofillId(), passwordView.getAutofillId()};

        if (usernameView != null)
            return new AutofillId[]{usernameView.getAutofillId()};

        if (passwordView != null)
            return new AutofillId[]{passwordView.getAutofillId()};

        return new AutofillId[0];
    }
}
