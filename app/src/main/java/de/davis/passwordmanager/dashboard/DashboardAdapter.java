package de.davis.passwordmanager.dashboard;

import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import de.davis.passwordmanager.R;
import de.davis.passwordmanager.dashboard.selection.KeyProvider;
import de.davis.passwordmanager.dashboard.selection.SecureElementDetailsLookup;
import de.davis.passwordmanager.dashboard.viewholders.BasicViewHolder;
import de.davis.passwordmanager.dashboard.viewholders.HeaderViewHolder;
import de.davis.passwordmanager.dashboard.viewholders.SecureElementViewHolder;
import de.davis.passwordmanager.security.element.SecureElement;

public class DashboardAdapter extends RecyclerView.Adapter<BasicViewHolder<?>> {

    private static final int HEADER_TYPE = 0;
    private static final int ITEM_TYPE = 1;

    private String filter;

    private final SparseArray<Header> headers;
    private final ArrayList<SecureElement> items;
    private SelectionTracker<Long> tracker;

    private StateChangeHandler stateChangeHandler;

    public DashboardAdapter(){
        headers = new SparseArray<>();
        items = new ArrayList<>();
        setHasStableIds(true);
    }

    public SelectionTracker<Long> getTracker() {
        return tracker;
    }

    public boolean isHeaderPosition(int realPosition){
        return headers.get(realPosition) != null;
    }

    private Header createHeader(@NonNull SecureElement item){
        return new Header(item.getLetter());
    }

    private SecureElementViewHolder onCreateItemViewHolder(@NonNull ViewGroup parent){
        return new SecureElementViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_element, parent, false));
    }

    @NonNull
    @Override
    public BasicViewHolder<?> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return viewType == HEADER_TYPE
                ? new HeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_header, parent, false))
                : onCreateItemViewHolder(parent);
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(@NonNull BasicViewHolder<?> holder, int position) {
        if(isHeaderPosition(position)){
            ((BasicViewHolder<Item>)holder).bind(headers.get(position), filter);
            return;
        }

        SecureElement data = getData(position);
        if(data == null)
            return;

        ((BasicViewHolder<SecureElement>)holder).bind(data, filter);
    }

    @Override
    public void onBindViewHolder(@NonNull BasicViewHolder<?> holder, int position, @NonNull List<Object> payloads) {
        if(payloads.isEmpty()){
            onBindViewHolder(holder, position);
            return;
        }

        //The payload represents whether an item can be selected or not.
        Object payload = payloads.get(0);
        if(Objects.equals(payload, "Selection-Changed"))
            payload = tracker.hasSelection();

        if(!(payload instanceof Boolean))
            return;

        handleSelectionUpdates(getItemId(position), holder);
    }

    @Override
    public long getItemId(int position) {
        return isHeaderPosition(position) ? headers.get(position).getId() : items.get(getDataPosition(position)).getId();
    }

    @Override
    public int getItemCount() {
        return items.size() + headers.size();
    }

    @Override
    public int getItemViewType(int position) {
        return isHeaderPosition(position) ? HEADER_TYPE : ITEM_TYPE;
    }

    public List<Item> getEntries(){
        List<Item> entries = new ArrayList<>(items.size()+headers.size());
        entries.addAll(items);
        for (int i = 0; i < headers.size(); i++) {
            entries.add(headers.keyAt(i), headers.valueAt(i));
        }

        return entries;
    }

    public SecureElement getData(int realPosition){
        int dataPosition = getDataPosition(realPosition);
        return dataPosition > RecyclerView.NO_POSITION ? items.get(dataPosition) : null;
    }

    public int getDataPosition(int realPosition){
        if(isHeaderPosition(realPosition))
            return RecyclerView.NO_POSITION;

        int offset = 0;
        for(int i = 0; i < headers.size(); i++){
            if(headers.keyAt(i) > realPosition)
                break;
            else
                offset--;
        }

        return realPosition + offset;
    }

    public int getRealPosition(int itemIndex){
        if(itemIndex < 0 || itemIndex >= items.size())
            return RecyclerView.NO_POSITION;

        for(int i = headers.size() - 1; i >= 0; i--){
            int key = headers.keyAt(i);
            if(key - i <= itemIndex)
                return itemIndex + i + 1;
        }

        return RecyclerView.NO_POSITION;
    }

    public int getRealPositionById(long id){
        for (int i = 0; i < headers.size(); i++) {
            int pos = headers.keyAt(i);
            if(getItemId(pos) == id)
                return pos;
        }


        return items.stream()
                .filter(item -> item.getId() == id)
                .findFirst()
                .map(item -> getRealPosition(items.indexOf(item)))
                .orElse(RecyclerView.NO_POSITION);
    }

    public List<Long> getIdsInHeader(Header header){
        int headerIndex = headers.indexOfValue(header);
        if(headerIndex < 0)
            return new ArrayList<>();

        int from = headers.keyAt(headerIndex) +1; //inclusive
        int to = headers.size() == headerIndex+1 ? getItemCount() : headers.keyAt(headerIndex+1); //exclusive

        return IntStream.range(from, to).mapToLong(this::getItemId).boxed().collect(Collectors.toList());
    }

    public Header getHeaderByRealPosition(int realPosition){
        return headers.get(realPosition);
    }

    public Header findHeaderByRealItemPosition(int realItemPosition){
        if(isHeaderPosition(realItemPosition))
            return headers.get(realItemPosition);

        for (int i = headers.size()-1; i >= 0; i--) {
            int position = headers.keyAt(i);
            if(position < realItemPosition)
                return headers.get(position);
        }

        return null;
    }

    public List<SecureElement> getSelectedElements(){
        return StreamSupport.stream(tracker.getSelection().spliterator(), false).map(this::getElementById).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public SecureElement getElementById(long id){
        int dataPosition = getDataPosition(getRealPositionById(id));
        if(dataPosition < 0)
            return null;

        return items.get(dataPosition);
    }

    public void applyWithTracker(RecyclerView recyclerView){
        recyclerView.setAdapter(this);
        tracker = new SelectionTracker.Builder<>(
                "tracker",
                recyclerView,
                new KeyProvider(recyclerView),
                new SecureElementDetailsLookup(recyclerView),
                StorageStrategy.createLongStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build();

        tracker.addObserver(new SelectionStateHandler(recyclerView));
    }

    private boolean shouldCreateHeader(int index){
        if (index == 0)
            return true;

        SecureElement previous = items.get(index - 1);
        SecureElement current = items.get(index);

        return previous == null || current == null || previous.getLetter() != current.getLetter();
    }

    private void prepareHeaderDataSet(){
        items.sort(Comparable::compareTo);

        List<Header> headersList = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            headersList.add(headers.valueAt(i));
        }

        headers.clear();

        for (int i = 0; i < items.size(); i++) {
            if (!shouldCreateHeader(i))
                continue;

            Header header = createHeader(items.get(i));
            Header finalHeader = header;
            boolean headerExists = headersList.stream().anyMatch(h -> h.getHeader() == finalHeader.getHeader());
            if(headerExists)
                header = headersList.stream().filter(h -> h.getHeader() == finalHeader.getHeader()).findFirst().orElseThrow(NullPointerException::new);

            headers.put(headers.size() + i, header);
        }
    }

    public void removeSelectedElements(){
        getTracker().clearSelection();
    }

    public void showOnly(List<SecureElement> elements){
        update(elements, null);
    }

    //If updateClass is null, all elements will be updated
    public void update(List<? extends SecureElement> overrideElements, Class<? extends SecureElement> updateClass){
        List<Item> oldEntries = getEntries();

        if(updateClass == null) items.clear();
        else items.removeIf(updateClass::isInstance);

        items.addAll(overrideElements);
        prepareHeaderDataSet();

        SecureElementDiffCallback callback = new SecureElementDiffCallback(oldEntries, getEntries());
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);
        result.dispatchUpdatesTo(this);
    }

    public void setStateChangeHandler(StateChangeHandler stateChangeHandler){
        this.stateChangeHandler = stateChangeHandler;
    }

    private void handleSelectionUpdates(long id, BasicViewHolder<?> viewHolder){
        int realPos = getRealPositionById(id);
        if(isHeaderPosition(realPos)){
            ((HeaderViewHolder)viewHolder).onChildSelected(getTracker().hasSelection(), getState(getHeaderByRealPosition(realPos)));
        }else
            viewHolder.onBindSelectablePayload(tracker.hasSelection(), tracker.isSelected(id));
    }

    private class SelectionStateHandler extends SelectionTracker.SelectionObserver<Long> implements RecyclerView.OnChildAttachStateChangeListener {

        private boolean hadSelections;
        private boolean changing;

        private final RecyclerView recyclerView;

        public SelectionStateHandler(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
            recyclerView.addOnChildAttachStateChangeListener(this);
        }

        @Override
        public void onItemStateChanged(@NonNull Long key, boolean selected) {
            if(changing)
                return;

            changing = true;
            long headerId = handleSelection(key, selected);

            changing = false;

            if(stateChangeHandler != null)
                stateChangeHandler.onStateChanged(getSelectedElements().size());

            // Update all items only if the selected item is the first or the last item that gets
            // selected
            if(tracker.hasSelection() == hadSelections)
                return;

            // To avoid re-notifying all observers to get their work done, this loop binds the
            // payload to the views directly. Differences do not have to be calculated.
            for (int i = 0; i < getItemCount(); i++) {
                long itemId = getItemId(i);
                if(itemId == key || itemId == headerId)
                    continue;

                BasicViewHolder<?> viewHolder = (BasicViewHolder<?>) recyclerView.findViewHolderForAdapterPosition(i);
                if(viewHolder == null)
                    continue;

                viewHolder.onBindSelectablePayload(tracker.hasSelection(), false);
            }

            hadSelections = tracker.hasSelection();
        }

        @Override
        public void onChildViewAttachedToWindow(@NonNull View view) {
            long id = getItemId(recyclerView.getChildAdapterPosition(view));
            BasicViewHolder<?> viewHolder = (BasicViewHolder<?>) recyclerView.findContainingViewHolder(view);
            if(viewHolder == null)
                return;

            handleSelectionUpdates(id, viewHolder);
        }

        @Override
        public void onChildViewDetachedFromWindow(@NonNull View view) {}

        /**
         * Handles each selection so that selecting all items of the same category also selects
         * the header. When the header is selected, all items with in this header are selected.
         * This also works the other way around.
         * @param id the id of the selected or deselected item.
         * @param selected true if the item got selected, false otherwise.
         */
        private long handleSelection(long id, boolean selected){
            int realItemPosition = getRealPositionById(id);
            if(isHeaderPosition(realItemPosition)){
                tracker.setItemsSelected(getIdsInHeader(getHeaderByRealPosition(realItemPosition)), selected);
                return id;
            }

            Header header = findHeaderByRealItemPosition(realItemPosition);
            if(header == null)
                return RecyclerView.NO_POSITION;

            long headerKey = header.getId();

            int state = getState(header);

            if(state == MaterialCheckBox.STATE_CHECKED) tracker.select(headerKey);
            else tracker.deselect(headerKey);

            HeaderViewHolder headerViewHolder = ((HeaderViewHolder)recyclerView.findViewHolderForItemId(headerKey));
            if(headerViewHolder != null)
                headerViewHolder.onChildSelected(getTracker().hasSelection(), state);

            return headerKey;
        }
    }

    private int getState(Header header){
        List<Long> idsInHeader = getIdsInHeader(header);
        int count = (int) idsInHeader
                .stream()
                .filter(itemId -> tracker.getSelection().contains(itemId)).count();

        int state;
        if(count == 0) state = MaterialCheckBox.STATE_UNCHECKED;
        else if(count == idsInHeader.size()) state = MaterialCheckBox.STATE_CHECKED;
        else state = MaterialCheckBox.STATE_INDETERMINATE;

        return state;
    }

    public interface StateChangeHandler{
        void onStateChanged(int selectedItems);
    }
}
