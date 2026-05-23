package github.com.gengyouno.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LocalMusicBookScreen extends Screen {
    private static final int MAX_TRACKS_PER_PAGE = 7;
    private static final int ROW_HEIGHT = 22;

    private String query = "";
    private String directUrl = "";
    private int page;
    private List<LocalMusicTrack> tracks = List.of();
    private List<LocalMusicTrack> filteredTracks = List.of();

    public LocalMusicBookScreen() {
        super(Component.translatable("screen.localmusicmod.music_book"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new LocalMusicBookScreen());
    }

    @Override
    protected void init() {
        reloadTracks();
        rebuildBookWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0.0D && page < pageCount() - 1) {
            page++;
            rebuildBookWidgets();
            return true;
        }
        if (scrollY > 0.0D && page > 0) {
            page--;
            rebuildBookWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void rebuildBookWidgets() {
        clearWidgets();

        int panelWidth = Math.min(360, width - 40);
        int left = (width - panelWidth) / 2;
        int y = Math.max(12, (height - 220) / 2);
        int tracksPerPage = tracksPerPage(y);

        addRenderableWidget(Button.builder(Component.translatable("screen.localmusicmod.music_book.refresh"), button -> {
            reloadTracks();
            rebuildBookWidgets();
        }).bounds(left, y, 78, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.localmusicmod.music_book.stop"), button -> LocalMusicPlayer.stop())
                .bounds(left + panelWidth - 156, y, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - 78, y, 78, 20).build());

        EditBox searchBox = new EditBox(font, left, y + 28, panelWidth, 20, Component.translatable("screen.localmusicmod.music_book.search"));
        searchBox.setHint(Component.translatable("screen.localmusicmod.music_book.search"));
        searchBox.setMaxLength(80);
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            query = value;
            page = 0;
            filterTracks();
            rebuildBookWidgets();
        });
        addRenderableWidget(searchBox);

        int directY = y + 56;
        EditBox urlBox = new EditBox(font, left, directY, panelWidth - 84, 20, Component.translatable("screen.localmusicmod.music_book.url"));
        urlBox.setHint(Component.translatable("screen.localmusicmod.music_book.url"));
        urlBox.setMaxLength(512);
        urlBox.setValue(directUrl);
        urlBox.setResponder(value -> directUrl = value.trim());
        addRenderableWidget(urlBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.localmusicmod.music_book.play_link"), button -> playDirectUrl())
                .bounds(left + panelWidth - 78, directY, 78, 20).build());

        int firstTrackY = y + 88;
        int start = page * tracksPerPage;
        int end = Math.min(filteredTracks.size(), start + tracksPerPage);
        if (filteredTracks.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.localmusicmod.music_book.empty"), button -> {
            }).bounds(left, firstTrackY, panelWidth, 20).build()).active = false;
        } else {
            for (int i = start; i < end; i++) {
                LocalMusicTrack track = filteredTracks.get(i);
                int row = i - start;
                Component label = Component.literal(track.displayName() + "  [" + track.id() + "]");
                addRenderableWidget(Button.builder(label, button -> LocalMusicPlayer.play(track))
                        .bounds(left, firstTrackY + row * ROW_HEIGHT, panelWidth, 20)
                        .build());
            }
        }

        int footerY = Math.min(height - 24, firstTrackY + tracksPerPage * ROW_HEIGHT + 4);
        Button previous = Button.builder(Component.translatable("screen.localmusicmod.music_book.previous"), button -> {
            page = Math.max(0, page - 1);
            rebuildBookWidgets();
        }).bounds(left, footerY, 78, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);

        int pageCount = pageCount(tracksPerPage);
        Button pageLabel = Button.builder(Component.literal((page + 1) + " / " + pageCount), button -> {
        }).bounds(left + 84, footerY, panelWidth - 168, 20).build();
        pageLabel.active = false;
        addRenderableWidget(pageLabel);

        Button next = Button.builder(Component.translatable("screen.localmusicmod.music_book.next"), button -> {
            page = Math.min(pageCount - 1, page + 1);
            rebuildBookWidgets();
        }).bounds(left + panelWidth - 78, footerY, 78, 20).build();
        next.active = page + 1 < pageCount;
        addRenderableWidget(next);
    }

    private void reloadTracks() {
        tracks = LocalMusicLibrary.loadTracks();
        filterTracks();
    }

    private void filterTracks() {
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        filteredTracks = tracks.stream()
                .filter(track -> lowerQuery.isEmpty()
                        || track.id().contains(lowerQuery)
                        || track.displayName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .toList();

        int pageCount = pageCount();
        if (page >= pageCount) {
            page = pageCount - 1;
        }
    }

    private void playDirectUrl() {
        if (directUrl.isBlank()) {
            return;
        }

        LocalMusicPlayer.play(new LocalMusicTrack("direct_link", "Direct Link", null, directUrl, 1.0F, false));
    }

    private int tracksPerPage(int y) {
        int firstTrackY = y + 88;
        int availableHeight = Math.max(ROW_HEIGHT, height - firstTrackY - 28);
        return Math.max(1, Math.min(MAX_TRACKS_PER_PAGE, availableHeight / ROW_HEIGHT));
    }

    private int tracksPerPage() {
        return tracksPerPage(Math.max(12, (height - 220) / 2));
    }

    private int pageCount() {
        return pageCount(tracksPerPage());
    }

    private int pageCount(int tracksPerPage) {
        return Math.max(1, (filteredTracks.size() + tracksPerPage - 1) / tracksPerPage);
    }
}
