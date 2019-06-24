package org.mineacademy.fo.menu;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.event.MenuOpenEvent;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.menu.button.Button;
import org.mineacademy.fo.menu.button.Button.DummyButton;
import org.mineacademy.fo.menu.button.ButtonReturnBack;
import org.mineacademy.fo.menu.model.InventoryDrawer;
import org.mineacademy.fo.menu.model.MenuClickLocation;
import org.mineacademy.fo.model.OneTimeRunnable;
import org.mineacademy.fo.model.SimpleSound;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.CompSound;

import lombok.Getter;
import lombok.Setter;

/**
 * The core class of Menu. Represents a simple menu.
 *
 * This is the recommended menu class for all plugins having menus. It offers
 * having a parent menu, a return button and an info button explaining the purpose
 * of the menu to the user.
 *
 * HOW TO GET STARTED: Place final {@link Button} fields in your menu class and make
 * a instatiate when in constructor. Those will be registered as clickable automatically.
 * To render them, override {@link #getItemAt(int)} and make them return at your desired
 * positions.
 */
public abstract class Menu {

	// --------------------------------------------------------------------------------
	// Static
	// --------------------------------------------------------------------------------

	/**
	 * The default sound when switching between menus.
	 */
	@Getter
	@Setter
	private static SimpleSound sound = new SimpleSound(CompSound.NOTE_STICKS.getSound(), .4F, 1F, true);

	/**
	 * An internal metadata tag the player gets when he opens the menu
	 *
	 * Used in {@link #getMenu(Player)}
	 */
	static final String TAG_CURRENT = "KaMenu_" + SimplePlugin.getNamed();

	/**
	 * An internal metadata tag the player gets when he opens another menu
	 *
	 * Used in {@link #getPreviousMenu(Player)}
	 */
	static final String TAG_PREVIOUS = "KaMenu_Previous_" + SimplePlugin.getNamed();

	/**
	 * Returns the current menu for player
	 *
	 * @param player the player
	 * @return the menu, or null if none
	 */
	public static final Menu getMenu(Player player) {
		return getMenu0(player, TAG_CURRENT);
	}

	/**
	 * Returns the previous menu for player
	 *
	 * @param player the player
	 * @return the menu, or none
	 */
	public static final Menu getPreviousMenu(Player player) {
		return getMenu0(player, TAG_PREVIOUS);
	}

	// Returns the menu associated with the players metadata, or null
	private static Menu getMenu0(Player player, String tag) {
		if (player.hasMetadata(tag)) {
			final Menu menu = (Menu) player.getMetadata(tag).get(0).value();
			Objects.requireNonNull(menu, "Menu missing from " + player.getName() + "'s metadata '" + tag + "' tag!");

			return menu;
		}

		return null;
	}

	// --------------------------------------------------------------------------------
	// Actual class
	// --------------------------------------------------------------------------------

	// --------------------------------------------------------------------------------
	// Buttons that are registered automatically (we scan fields in your menu class)
	// --------------------------------------------------------------------------------

	/**
	 * Automatically registered Buttons in this menu (using reflection)
	 */
	private final List<Button> registeredButtons = new ArrayList<>();

	/**
	 * The registrator responsible for scanning the class and making buttons
	 * function
	 */
	private final OneTimeRunnable buttonsRegistrator;

	// --------------------------------------------------------------------------------
	// Other constructors
	// --------------------------------------------------------------------------------

	/**
	 * Parent menu
	 */
	private final Menu parent;

	/**
	 * The return button to the previous menu, null if none
	 */
	private final Button returnButton;

	/**
	 * The size of the menu
	 */
	private Integer size = 9 * 3;

	/**
	 * The inventory title of the menu
	 */
	private String title;

	/**
	 * The viewer of this menu, is null until {@link #displayTo(Player)} is called
	 */
	private Player viewer;

	/**
	 * Create a new menu without parent menu with the size of 9*3
	 *
	 * You are encouraged to change the size and title of this menu in your constructor
	 * by calling {@link #setTitle(String)} and {@link #setSize(Integer)}
	 *
	 * NB: The {@link #getViewer()} of this menu is yet null!
	 */
	protected Menu() {
		this(null);
	}

	/**
	 * Create a new menu with parent menu with the size of 9*3
	 *
	 * You are encouraged to change the size and title of this menu in your constructor
	 * by calling {@link #setTitle(String)} and {@link #setSize(Integer)}
	 *
	 * NB: The {@link #getViewer()} of this menu is yet null!
	 *
	 * @param parent the parent menu
	 */
	protected Menu(Menu parent) {
		this(parent, false);
	}

	/**
	 * Create a new menu with parent menu with the size of 9*3
	 *
	 * You are encouraged to change the size and title of this menu in your constructor
	 * by calling {@link #setTitle(String)} and {@link #setSize(Integer)}
	 *
	 * NB: The {@link #getViewer()} of this menu is yet null!
	 *
	 * @param parent                 the parent
	 * @param returnMakesNewInstance should we re-instatiate the parent menu when
	 *                               returning to it?
	 */
	protected Menu(Menu parent, boolean returnMakesNewInstance) {
		this.parent = parent;
		this.returnButton = parent != null ? new ButtonReturnBack(parent, returnMakesNewInstance) : Button.makeEmpty();
		this.buttonsRegistrator = new OneTimeRunnable(() -> registerButtons());
	}

	// --------------------------------------------------------------------------------
	// Reflection to make life easier
	// --------------------------------------------------------------------------------

	/**
	 * Scans the menu class this menu extends and registers buttons
	 */
	final void registerButtons() {
		registeredButtons.clear();

		// Register buttons explicitly given
		{
			final List<Button> buttons = getButtonsToAutoRegister();

			if (buttons != null)
				registeredButtons.addAll(buttons);
		}

		// Register buttons declared as fields
		{
			Class<?> lookup = getClass();

			do {
				for (final Field f : lookup.getDeclaredFields())
					registerButton0(f);

			} while (Menu.class.isAssignableFrom(lookup = lookup.getSuperclass()));
		}
	}

	// Scans the class and register fields that extend Button class
	private final void registerButton0(Field field) {
		field.setAccessible(true);

		final Class<?> type = field.getType();

		if (Button.class.isAssignableFrom(type)) {
			final Button button = (Button) ReflectionUtil.getFieldContent(field, this);

			Objects.requireNonNull(button, "Null button field named " + field.getName() + " in " + this);
			registeredButtons.add(button);
		}

		else if (Button[].class.isAssignableFrom(type)) {
			Validate.isTrue(Modifier.isFinal(field.getModifiers()), "Report / Button[] field must be final: " + field);
			final Button[] buttons = (Button[]) ReflectionUtil.getFieldContent(field, this);

			Validate.isTrue(buttons != null && buttons.length > 0, "Null " + field.getName() + "[] in " + this);
			registeredButtons.addAll(Arrays.asList(buttons));
		}
	}

	/**
	 * Returns a list of buttons that should be registered manually.
	 *
	 * NOTICE: Button fields in your class are registered automatically, do not add them here
	 *
	 * @return button list, null by default
	 */
	protected List<Button> getButtonsToAutoRegister() {
		return null;
	}

	/**
	 * Attempts to find a clickable registered button in this menu having the same
	 * icon as the given item stack
	 *
	 * @param fromItem the itemstack to compare to
	 * @return the buttor or null if not found
	 */
	final Button getButton(ItemStack fromItem) {
		buttonsRegistrator.runIfHasnt();

		if (fromItem != null)
			for (final Button button : registeredButtons) {
				Objects.requireNonNull(button, "Menu button is null at " + getClass().getSimpleName());
				Objects.requireNonNull(button.getItem(), "Itemstack cannot be null at " + button.getClass().getSimpleName());

				try {
					if (button.getItem().equals(fromItem))
						return button;
				} catch (final NullPointerException ex) {
				}
			}

		return null;
	}

	/**
	 * Return a new instance of this menu
	 *
	 * You must override this in certain cases
	 *
	 * @throws if new instance could not be made, for example when the menu is
	 *         taking constructor params
	 * @return the new instance, of null
	 */
	public Menu newInstance() {
		try {
			return ReflectionUtil.instatiate(getClass());
		} catch (final Throwable t) {

			try {
				final Object parent = getClass().getMethod("getParent").invoke(getClass());

				if (parent != null)
					return ReflectionUtil.instatiate(getClass(), parent);
			} catch (final Throwable tt) {
			}
		}

		throw new FoException("Could not make new instance of menu " + getClass() + ", please override 'newInstance'!");
	}

	// --------------------------------------------------------------------------------
	// Rendering the menu
	// --------------------------------------------------------------------------------

	/**
	 * Displays this menu to the player
	 *
	 * The menu will not be displayed when the player is having server conversation
	 *
	 * @param player the player
	 */
	public final void displayTo(Player player) {
		displayTo(player, false);
	}

	/**
	 * Display this menu to the player
	 *
	 * @param player                   the player
	 * @param ignoreServerConversation display menu even if the player is having
	 *                                 server conversation?
	 */
	public final void displayTo(Player player, boolean ignoreServerConversation) {
		Objects.requireNonNull(size, "Size not set in " + this);
		Objects.requireNonNull(title, "Title not set in " + this);

		this.viewer = player;
		this.buttonsRegistrator.runIfHasnt();

		// Draw the menu
		final InventoryDrawer drawer = InventoryDrawer.of(size, title);

		// Compile bottom bar
		compileBottomBar0().forEach((slot, item) -> drawer.setItem(slot, item));

		// Set items defined by classes upstream
		for (int i = 0; i < drawer.getSize(); i++) {
			final ItemStack item = getItemAt(i);

			if (item != null && !drawer.isSet(i))
				drawer.setItem(i, item);
		}

		// Allow last minute modifications
		onDisplay(drawer);

		// Call event after items have been set to allow to get them
		if (!Common.callEvent(new MenuOpenEvent(this, drawer, player)))
			return;

		// Prevent menu in conversation
		if (!ignoreServerConversation && player.isConversing()) {
			player.sendRawMessage(ChatColor.RED + "Type 'exit' to quit your conversation before opening menu.");

			return;
		}

		// Play the pop sound
		sound.play(player);

		// Register previous menu if exists
		{
			final Menu previous = getMenu(player);

			if (previous != null)
				player.setMetadata(TAG_PREVIOUS, new FixedMetadataValue(SimplePlugin.getInstance(), previous));
		}

		// Register current menu
		Common.runLater(1, () -> {
			drawer.display(player);

			player.setMetadata(TAG_CURRENT, new FixedMetadataValue(SimplePlugin.getInstance(), Menu.this));
		});
	}

	/**
	 * Called automatically before the menu is displayed but after all
	 * items have been drawed
	 *
	 * Override for custom last-minute modifications
	 *
	 * @param drawer the drawer
	 */
	protected void onDisplay(InventoryDrawer drawer) {
	}

	/**
	 * Redraws and refreshes all buttons
	 */
	public final void restartMenu() {
		restartMenu(null);
	}

	/**
	 * Redraws and re-register all buttons while sending a title animation to the
	 * player
	 *
	 * @param animatedTitle the animated title
	 */
	public final void restartMenu(String animatedTitle) {
		registerButtons();
		redraw();

		if (animatedTitle != null)
			animateTitle(animatedTitle);
	}

	/**
	 * Redraws the bottom bar and updates inventory
	 */
	final void redraw() {
		final Inventory inv = getViewer().getOpenInventory().getTopInventory();
		Validate.isTrue(inv.getType() == InventoryType.CHEST, getViewer().getName() + "'s inventory closed in the meanwhile (now == " + inv.getType() + ").");

		for (int i = 0; i < size; i++) {
			final ItemStack item = getItemAt(i);

			Validate.isTrue(i < inv.getSize(), "Item (" + (item != null ? item.getType() : "null") + ") position (" + i + ") > inv size (" + inv.getSize() + ")");
			inv.setItem(i, item);
		}

		compileBottomBar0().forEach((slot, item) -> inv.setItem(slot, item));
		getViewer().updateInventory();
	}

	/**
	 * Draws the bottom bar for the player inventory
	 *
	 * @return
	 */
	private final Map<Integer, ItemStack> compileBottomBar0() {
		final Map<Integer, ItemStack> items = new HashMap<>();

		if (getInfo() != null)
			items.put(getInfoButtonPosition(), Button.makeInfo(getInfo()).getItem());

		if (addReturnButton() && !(returnButton instanceof DummyButton))
			items.put(getReturnButtonPosition(), returnButton.getItem());

		return items;
	}

	/**
	 * Animate the title of this menu
	 *
	 * Automatically reverts back to the old title after 1 second
	 *
	 * @param title the title to animate
	 */
	public final void animateTitle(String title) {
		PlayerUtil.animateInvTitle(this, getViewer(), title, getTitle());
	}

	// --------------------------------------------------------------------------------
	// Menu functions
	// --------------------------------------------------------------------------------

	/**
	 * Returns the item at a certain slot
	 *
	 * @param slot the slow
	 * @return the item, or null if no icon at the given slot (default)
	 */
	public ItemStack getItemAt(int slot) {
		return null;
	}

	/**
	 * Get the information about this menu.
	 *
	 * Used to create an info bottom in bottom left corner, see
	 * {@link Button#makeInfo(String...)}
	 *
	 * @return the description of this menu, or null
	 */
	protected abstract String[] getInfo();

	/**
	 * Get the info button position
	 *
	 * @return the slot which info buttons is located on
	 */
	protected int getInfoButtonPosition() {
		return size - 9;
	}

	/**
	 * Should we automatically add the return button to the bottom left corner?
	 *
	 * @return true if the return button should be added, true by default
	 */
	protected boolean addReturnButton() {
		return true;
	}

	/**
	 * Get the return button position
	 *
	 * @return the slot which return buttons is located on
	 */
	protected int getReturnButtonPosition() {
		return size - 1;
	}

	/**
	 * Calculates the center slot of this menu
	 *
	 * @deprecated not exact
	 * @return the estimated center slot
	 */
	@Deprecated
	protected final int getCenterSlot() {
		int pos = Arrays.asList(13, 22, 31).contains(pos = size / 2) ? pos : pos - 5;

		return pos;
	}

	/**
	 * Should we prevent the click or drag?
	 *
	 * @param location the click location
	 * @param slot     the slot
	 * @param clicked  the clicked item
	 * @param cursor   the cursor
	 * @deprecated sometimes does not work correctly due to flaws in server to
	 *             client packet communication do not rely on this
	 * @return if the action is cancelled in the {@link InventoryClickEvent}, false
	 *         by default
	 */
	@Deprecated
	protected boolean isActionAllowed(MenuClickLocation location, int slot, ItemStack clicked, ItemStack cursor) {
		return false;
	}

	/**
	 * The title of this menu
	 *
	 * @return the menu title
	 */
	public final String getTitle() {
		return title;
	}

	/**
	 * Sets the title of this inventory, adding black color at the front
	 * automatically - this change is not reflected in client, you must
	 * call {@link #restartMenu()} to take change
	 *
	 * @param title the new title
	 */
	protected final void setTitle(String title) {
		this.title = "&0" + title;
	}

	/**
	 * Return the parent menu or null
	 *
	 * @return
	 */
	public final Menu getParent() {
		return parent;
	}

	/**
	 * Get the size of this menu
	 *
	 * @return
	 */
	public final Integer getSize() {
		return size;
	}

	/**
	 * Sets the size of this menu (without updating the player
	 * container - if you want to update it call {@link #restartMenu()})
	 *
	 * @param size
	 */
	protected final void setSize(Integer size) {
		this.size = size;
	}

	/**
	 * Get the viewer that this instance of this menu is associated with
	 *
	 * @return the viewer of this instance, or null
	 */
	protected final Player getViewer() {
		return viewer;
	}

	/**
	 * Sets the viewer for this instance of this menu
	 *
	 * @param viewer
	 */
	protected final void setViewer(Player viewer) {
		this.viewer = viewer;
	}

	// --------------------------------------------------------------------------------
	// Events
	// --------------------------------------------------------------------------------

	/**
	 * Called automatically when the menu is clicked.
	 *
	 * By default we call the shorter {@link #onMenuClick(Player, int, ItemStack)}
	 * method.
	 *
	 * @param player    the player
	 * @param slot      the slot
	 * @param action    the action
	 * @param click     the click
	 * @param cursor    the cursor
	 * @param clicked   the item clicked
	 * @param cancelled is the event cancelled?
	 */
	protected void onMenuClick(Player player, int slot, InventoryAction action, ClickType click, ItemStack cursor, ItemStack clicked, boolean cancelled) {
		onMenuClick(player, slot, clicked);
	}

	/**
	 * Called automatically when the menu is clicked
	 *
	 * @param player  the player
	 * @param slot    the slot
	 * @param clicked the item clicked
	 */
	protected void onMenuClick(Player player, int slot, ItemStack clicked) {
	}

	/**
	 * Called automatically when a registered button is clicked
	 *
	 * By default this method parses the click into
	 * {@link Button#onClickedInMenu(Player, Menu, ClickType)}
	 *
	 * @param player the player
	 * @param slot   the slot
	 * @param action the action
	 * @param click  the click
	 * @param button the button
	 */
	protected void onButtonClick(Player player, int slot, InventoryAction action, ClickType click, Button button) {
		button.onClickedInMenu(player, this, click);
	}

	/**
	 * Called automatically when the menu is closed
	 *
	 * @param player    the player
	 * @param inventory the menu inventory that is being closed
	 */
	protected void onMenuClose(Player player, Inventory inventory) {
	}

	@Override
	public final boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{}";
	}
}
