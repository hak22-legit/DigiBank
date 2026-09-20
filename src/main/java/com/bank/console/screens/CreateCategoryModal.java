package com.bank.console.screens;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.CategoryController;
import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Interactive 82-column modal for creating a custom category.
 * Enforces length constraints (3-20 characters), duplicate validation,
 * and clears screen buffer to avoid terminal bleed.
 */
public class CreateCategoryModal {
    private static final Logger logger = LoggerFactory.getLogger(CreateCategoryModal.class);

    public static final String TITLE = "DIGIBANK CORE > FINANCIAL PLANNING > CREATE CUSTOM CATEGORY";
    public static final String COMPARTMENT_HEADER = "CREATE CATEGORY SPECIFICATIONS";
    public static final String FOOTER = " [Tab/↓] Next Field  •  [Enter] Confirm/Select  •  [1/2] Action  •  [Esc] Back";

    public static Category show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                CategoryController categoryController, User userEntity) {
        return show(terminal, origAttr, reader, categoryController, userEntity, TUILayout.APP_WIDTH);
    }

    public static Category show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                CategoryController categoryController, User userEntity, int width) {
        StringBuilder nameBuf = new StringBuilder();
        StringBuilder descBuf = new StringBuilder();
        int focusedField = 0; // 0: Name, 1: Description, 2: Actions
        int actionIdx = 0;    // 0: Save, 1: Cancel
        String statusMessage = "Name must be between 3 and 20 characters.";
        boolean isError = false;

        boolean firstRender = true;
        try {
            while (true) {
                String modal = renderModalContent(nameBuf.toString(), descBuf.toString(),
                        focusedField, actionIdx, statusMessage, isError, width);
                ScreenRenderer.render(modal, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    return null;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 3;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 3) % 3;
                } else if (focusedField == 2 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 0 && nameBuf.length() > 0) {
                        nameBuf.deleteCharAt(nameBuf.length() - 1);
                    } else if (focusedField == 1 && descBuf.length() > 0) {
                        descBuf.deleteCharAt(descBuf.length() - 1);
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        focusedField = 1;
                    } else if (focusedField == 1) {
                        focusedField = 2;
                    } else if (focusedField == 2) {
                        if (actionIdx == 0) {
                            Category saved = attemptSave(nameBuf.toString(), descBuf.toString(), categoryController, userEntity);
                            if (saved != null) {
                                return saved;
                            }
                            isError = true;
                            statusMessage = getValidationError(nameBuf.toString(), categoryController, userEntity);
                        } else {
                            return null;
                        }
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 2) {
                        if (c == '1') {
                            Category saved = attemptSave(nameBuf.toString(), descBuf.toString(), categoryController, userEntity);
                            if (saved != null) {
                                return saved;
                            }
                            isError = true;
                            statusMessage = getValidationError(nameBuf.toString(), categoryController, userEntity);
                        } else if (c == '2') {
                            return null;
                        }
                    } else if (focusedField == 0) {
                        if (nameBuf.length() < 20 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')) {
                            nameBuf.append(c);
                        }
                    } else if (focusedField == 1) {
                        if (descBuf.length() < 50 && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' || c == '.' || c == ',')) {
                            descBuf.append(c);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in CreateCategoryModal", e);
            return null;
        }
    }

    public static String getValidationError(String name, CategoryController categoryController, User userEntity) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.length() < 3 || trimmed.length() > 20) {
            return "Name must be between 3 and 20 characters.";
        }
        if (categoryController != null && userEntity != null) {
            try {
                List<Category> categories = categoryController.getVisibleCategories(userEntity);
                boolean exists = categories.stream().anyMatch(c -> c.getName().equalsIgnoreCase(trimmed));
                if (exists) {
                    return "Category already exists: " + trimmed;
                }
            } catch (Exception ignored) {}
        }
        return "Validation failed. Please check inputs.";
    }

    private static Category attemptSave(String name, String desc, CategoryController categoryController, User userEntity) {
        String trimmed = name != null ? name.trim() : "";
        if (trimmed.length() < 3 || trimmed.length() > 20) {
            return null;
        }
        if (categoryController == null || userEntity == null) {
            return Category.builder().name(trimmed).description(desc).system(false).build();
        }
        try {
            List<Category> categories = categoryController.getVisibleCategories(userEntity);
            boolean exists = categories.stream().anyMatch(c -> c.getName().equalsIgnoreCase(trimmed));
            if (exists) {
                return null;
            }
            return categoryController.createCustomCategory(trimmed, desc != null ? desc.trim() : "", userEntity);
        } catch (Exception e) {
            logger.warn("Failed to create custom category", e);
            return null;
        }
    }

    public static String renderModalContent(String name, String desc, int focusedField, int actionIdx,
                                            String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(TITLE), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(COMPARTMENT_HEADER, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIFormHelper.formatFieldRow("Category Name", name, focusedField == 0, 20, 50)).append("\n");
        sb.append(TUIFormHelper.formatInfoRow("Classification", "(1) EXPENSE", 20, 50)).append("\n");
        sb.append(TUIFormHelper.formatFieldRow("Description (Opt)", desc, focusedField == 1, 20, 50)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String a1 = "[1] Save Category";
        String a2 = "[2] Cancel & Return";
        String act1 = (focusedField == 2 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
        String act2 = (focusedField == 2 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
        sb.append(TUIBox.line("  " + act1 + "                         " + act2, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        String formattedStatus = isError ? ConsoleTheme.error(statusMessage) : statusMessage;
        sb.append(TUIBox.line("Status: " + formattedStatus, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide(FOOTER)).append("\n");

        return sb.toString();
    }
}
