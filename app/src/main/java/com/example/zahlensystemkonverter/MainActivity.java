
package com.example.zahlensystemkonverter;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.text.Editable;
import android.text.TextWatcher;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputFilter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private EditText inputField;
    private TextView resultField;
    private TextView statusLabel;

    private Spinner fromBase;
    private Spinner toBase;

    private ListView historyList;

    private ArrayList<HistoryEntry> history;
    private ArrayAdapter<String> historyAdapter;

    private int selectedHistoryIndex = -1;

    private final int[] bases = {2, 8, 10, 16};

    private final String[] baseNames = {
            "Bin (2)",
            "Okt (8)",
            "Dez (10)",
            "Hex (16)"
    };
    private static final String PREFS_NAME = "converter_prefs";
    private static final String KEY_HISTORY = "history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views verbinden
        inputField = findViewById(R.id.inputField);
        inputField.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        inputField.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                liveConvert();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        resultField = findViewById(R.id.resultField);
        statusLabel = findViewById(R.id.statusLabel);

        fromBase = findViewById(R.id.fromBase);
        toBase = findViewById(R.id.toBase);

        historyList = findViewById(R.id.historyList);

        // Zahlensysteme laden
        ArrayAdapter<String> baseAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        baseNames
                );

        baseAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        fromBase.setAdapter(baseAdapter);
        toBase.setAdapter(baseAdapter);

        // Standard: Dezimal zu Binär
        fromBase.setSelection(2);
        toBase.setSelection(0);

        AdapterView.OnItemSelectedListener baseListener =
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        liveConvert();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                };

        fromBase.setOnItemSelectedListener(baseListener);
        toBase.setOnItemSelectedListener(baseListener);

        // Verlauf vorbereiten
        history = new ArrayList<>();

        historyAdapter = new ArrayAdapter<>(

                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<String>()
        );

        historyList.setAdapter(historyAdapter);

        loadSavedHistory();

        historyList.setEmptyView(findViewById(R.id.emptyHistory));

        // Verlauf auswählen
        historyList.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedHistoryIndex = position;
                    statusLabel.setText("Verlaufseintrag ausgewählt.");
                }
        );

        // Buttons verbinden

        findViewById(R.id.swapButton)
                .setOnClickListener(v -> swapBases());

        findViewById(R.id.copyButton)
                .setOnClickListener(v -> copyResult());
        findViewById(R.id.shareButton)
                .setOnClickListener(v -> shareResult());

        findViewById(R.id.resetButton)
                .setOnClickListener(v -> resetAll());

        findViewById(R.id.loadHistoryButton)
                .setOnClickListener(v -> loadHistory());

        findViewById(R.id.deleteHistoryButton)
                .setOnClickListener(v -> clearHistory());

        // Enter führt Umrechnung aus
        inputField.setOnEditorActionListener(
                (v, actionId, event) -> {
                    convertNumber();
                    return true;
                }
        );
    }

    // Umrechnung
    private void convertNumber() {

        String input = inputField.getText()
                .toString().trim();

        if (input.isEmpty()) {
            inputField.setError("Bitte eine Zahl eingeben!");
            return;
        }

        int sourceBase = bases[fromBase.getSelectedItemPosition()];
        int targetBase = bases[toBase.getSelectedItemPosition()];

        try {

            validateInput(input, sourceBase);

            BigInteger number = new BigInteger(input, sourceBase);

            String result = number.toString(targetBase)
                    .toUpperCase(Locale.ROOT);

            resultField.setText(result);

            setStatus("Umrechnung erfolgreich!", false);

            // Verlauf speichern
            HistoryEntry entry = new HistoryEntry(
                    input,
                    result,
                    sourceBase,
                    targetBase
            );

            boolean isDuplicate = !history.isEmpty()
                    && history.get(0).input.equals(input)
                    && history.get(0).sourceBase == sourceBase
                    && history.get(0).targetBase == targetBase;

            if (!isDuplicate) {
                history.add(0, entry);

                if (history.size() > 100) {
                    history.remove(history.size() - 1);
                }
            }

            updateHistory();
            saveHistory();

            selectedHistoryIndex = -1;

        } catch (NumberFormatException e) {

            inputField.setError(
                    "Ungültige Zahl für dieses Zahlensystem!"
            );

            resultField.setText("");

            setStatus("Fehler bei der Eingabe!", true);
        }
    }

    private void liveConvert() {

        TextView stepsField = findViewById(R.id.stepsField);

        String input = inputField.getText().toString().trim();

        if (input.isEmpty()) {
            resultField.setText("Ergebnis erscheint hier");
            stepsField.setText("");
            inputField.setError(null);
            return;
        }

        int sourceBase = bases[fromBase.getSelectedItemPosition()];
        int targetBase = bases[toBase.getSelectedItemPosition()];

        try {
            validateInput(input, sourceBase);

            BigInteger number = new BigInteger(input, sourceBase);

            resultField.setText(
                    number.toString(targetBase).toUpperCase(Locale.ROOT)
            );

            String expansion = "";
            if (sourceBase != 10) {
                expansion = buildExpansionSteps(input, sourceBase);
            }

            String division = "";
            if (targetBase != 10) {
                division = buildDivisionSteps(number, targetBase);
            }

            StringBuilder all = new StringBuilder();

            if (!expansion.isEmpty()) {
                all.append("Nach Dezimal:\n")
                        .append(expansion)
                        .append("\n");
            }

            if (!division.isEmpty()) {
                if (all.length() > 0) {
                    all.append("\n");
                }
                all.append("Nach Basis ")
                        .append(targetBase)
                        .append(":\n")
                        .append(division);
            }

            stepsField.setText(all.toString());

            inputField.setError(null);

        } catch (NumberFormatException e) {
            resultField.setText("Ungültige Eingabe");
            stepsField.setText("");
        }
    }

    // Eingabe überprüfen
    private void validateInput(String input, int base) {

        int start = 0;

        if (input.startsWith("-") || input.startsWith("+")) {
            start = 1;
        }

        if (start == input.length()) {
            throw new NumberFormatException();
        }

        for (int i = start; i < input.length(); i++) {

            char c = input.charAt(i);

            boolean validCharacter =
                    (c >= '0' && c <= '9') ||
                            (c >= 'A' && c <= 'Z') ||
                            (c >= 'a' && c <= 'z');

            int digit = Character.digit(c, 36);

            if (!validCharacter || digit < 0 || digit >= base) {
                throw new NumberFormatException();
            }
        }
    }

    // Zahlensysteme tauschen
    private void swapBases() {

        int first = fromBase.getSelectedItemPosition();
        int second = toBase.getSelectedItemPosition();

        fromBase.setSelection(second);
        toBase.setSelection(first);

        setStatus("Zahlensysteme getauscht.", false);
    }

    // Ergebnis kopieren
    private void copyResult() {

        String result = resultField.getText().toString();

        if (result.isEmpty()
                || result.equals("Ergebnis erscheint hier")
                || result.equals("Ungültige Eingabe")) {
            setStatus("Kein Ergebnis zum Kopieren!", true);
            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE
                );

        ClipData clip = ClipData.newPlainText(
                "Umrechnungsergebnis",
                result
        );

        clipboard.setPrimaryClip(clip);

        Toast.makeText(
                this,
                "Ergebnis wurde kopiert!",
                Toast.LENGTH_SHORT
        ).show();

        setStatus("Ergebnis kopiert.", false);
    }

    private void shareResult() {

        String result = resultField.getText().toString();

        if (result.isEmpty()
                || result.equals("Ergebnis erscheint hier")
                || result.equals("Ungültige Eingabe")) {
            setStatus("Kein Ergebnis zum Teilen!", true);
            return;
        }

        String input = inputField.getText().toString().trim();
        int sourceBase = bases[fromBase.getSelectedItemPosition()];
        int targetBase = bases[toBase.getSelectedItemPosition()];

        String steps = ((TextView) findViewById(R.id.stepsField))
                .getText().toString();

        StringBuilder sb = new StringBuilder();

        sb.append("Zahlensystem-Konverter\n")
                .append(input)
                .append(" (Basis ").append(sourceBase).append(")")
                .append(" → ")
                .append(result)
                .append(" (Basis ").append(targetBase).append(")");

        if (!steps.isEmpty()) {
            sb.append("\n\n").append(steps);
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());

        startActivity(Intent.createChooser(intent, "Teilen"));
    }

    // Alles zurücksetzen
    private void resetAll() {

        inputField.setText("");
        resultField.setText("Ergebnis erscheint hier");

        fromBase.setSelection(2);
        toBase.setSelection(0);

        historyList.clearChoices();
        selectedHistoryIndex = -1;

        setStatus("Bereit", false);
    }

    // Verlauf aktualisieren

    private void saveHistory() {

        StringBuilder sb = new StringBuilder();

        for (HistoryEntry e : history) {
            sb.append(e.input).append("|")
                    .append(e.result).append("|")
                    .append(e.sourceBase).append("|")
                    .append(e.targetBase).append("\n");
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY, sb.toString())
                .apply();
    }

    private void loadSavedHistory() {

        String saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_HISTORY, "");

        if (saved.isEmpty()) {
            return;
        }

        for (String line : saved.split("\n")) {

            String[] parts = line.split("\\|");

            if (parts.length != 4) {
                continue;
            }

            try {
                history.add(new HistoryEntry(
                        parts[0],
                        parts[1],
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                ));
            } catch (NumberFormatException e) {
                // skip broken line
            }
        }

        updateHistory();
    }
    private void updateHistory() {

        historyAdapter.clear();

        for (HistoryEntry entry : history) {

            String text =
                    entry.input + " (B" + entry.sourceBase + ")" +
                            "  →  " +
                            entry.result + " (B" + entry.targetBase + ")";

            historyAdapter.add(text);
        }

        historyAdapter.notifyDataSetChanged();
    }

    private String buildDivisionSteps(BigInteger number, int base) {

        StringBuilder sb = new StringBuilder();

        if (number.signum() <= 0) {
            return "";
        }

        BigInteger b = BigInteger.valueOf(base);
        BigInteger current = number;

        while (current.signum() > 0) {

            BigInteger[] qr = current.divideAndRemainder(b);

            char digit = Character.toUpperCase(
                    Character.forDigit(qr[1].intValue(), base)
            );

            sb.append(current)
                    .append(" : ")
                    .append(base)
                    .append(" = ")
                    .append(qr[0])
                    .append(" Rest ")
                    .append(qr[1]).append(" (").append(digit).append(")")
                    .append("\n");

            current = qr[0];
        }
        sb.append("Reste von unten nach oben lesen");

        return sb.toString();
    }

    private String buildExpansionSteps(String input, int base) {

        String digits = input.toUpperCase(Locale.ROOT);

        if (digits.startsWith("-") || digits.startsWith("+")) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int length = digits.length();

        for (int i = 0; i < length; i++) {

            int digit = Character.digit(digits.charAt(i), base);
            int power = length - 1 - i;

            if (i > 0) {
                sb.append(" + ");
            }

            sb.append(digit)
                    .append("×")
                    .append(base)
                    .append("^")
                    .append(power);
        }

        sb.append(" = ").append(new BigInteger(digits, base));

        return sb.toString();
    }

    // Verlauf laden
    private void loadHistory() {

        if (selectedHistoryIndex < 0 ||
                selectedHistoryIndex >= history.size()) {

            setStatus(
                    "Bitte zuerst einen Verlaufseintrag auswählen!",
                    true
            );

            return;
        }

        HistoryEntry entry = history.get(selectedHistoryIndex);

        inputField.setText(entry.input);

        fromBase.setSelection(getBaseIndex(entry.sourceBase));
        toBase.setSelection(getBaseIndex(entry.targetBase));

        resultField.setText(entry.result);

        setStatus("Verlaufseintrag geladen.", false);
    }

    // Systemindex finden
    private int getBaseIndex(int base) {

        for (int i = 0; i < bases.length; i++) {

            if (bases[i] == base) {
                return i;
            }
        }

        return 0;
    }

    // Verlauf löschen
    private void clearHistory() {

        if (history.isEmpty()) {
            setStatus("Der Verlauf ist bereits leer.", false);
            return;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Verlauf löschen")
                .setMessage(
                        "Möchten Sie den gesamten Verlauf löschen?"
                )
                .setPositiveButton("Ja", (dialog, which) -> {

                    history.clear();
                    saveHistory();
                    historyAdapter.clear();

                    selectedHistoryIndex = -1;
                    historyList.clearChoices();

                    setStatus("Verlauf wurde gelöscht.", false);
                })
                .setNegativeButton("Nein", null)
                .show();
    }

    // Statusmeldung
    private void setStatus(String message, boolean error) {

        statusLabel.setText(message);

        statusLabel.setTextColor(
                error ? 0xFFCC3333 : 0xFF268C64
        );
    }

    // Datenklasse für Verlauf
    private static class HistoryEntry {

        String input;
        String result;

        int sourceBase;
        int targetBase;

        HistoryEntry(
                String input,
                String result,
                int sourceBase,
                int targetBase
        ) {

            this.input = input;
            this.result = result;

            this.sourceBase = sourceBase;
            this.targetBase = targetBase;
        }
    }
}
