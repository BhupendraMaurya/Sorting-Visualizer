import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean; // For thread safety with stopping

public class SortingVisualizer extends JFrame {
    // --- Constants and Volatile Variables for UI/Algorithm State ---
    private volatile int ARRAY_SIZE = 50;
    private volatile int ELEMENT_WIDTH; // Calculated based on FRAME_WIDTH and ARRAY_SIZE
    private static final int FRAME_WIDTH = 1000; // Reduced width to better fit many bars
    private static final int FRAME_HEIGHT = 500;
    private volatile int DELAY_MS = 50; // Initial delay for visualization speed

    private int[] array; // The array being sorted

    // UI Components
    private JComboBox<String> algorithmComboBox;
    private JButton restartButton;
    private JButton pauseResumeButton;
    private JSlider speedSlider;
    private JSlider arraySizeSlider;
    private JLabel algorithmInfoLabel;
    private JLabel swapCountLabel;
    private JLabel comparisonCountLabel;

    // --- State Variables for Visualization ---
    private volatile int comparingIndex1 = -1; // Index of first element being compared
    private volatile int comparingIndex2 = -1; // Index of second element being compared
    private volatile int swappingIndex1 = -1; // Index of first element being swapped
    private volatile int swappingIndex2 = -1; // Index of second element being swapped
    private volatile int pivotIndex = -1;     // Index of the pivot element (for Quick Sort)
    private volatile int sortedElementBoundary = -1; // Index up to which elements are sorted (e.g., Bubble, Selection)
    private volatile boolean isSorted = false; // Flag to indicate if the array is fully sorted

    // --- Thread Control Variables ---
    private volatile boolean isPaused = false;
    private final Object pauseLock = new Object(); // Object used for thread synchronization during pause
    private Thread currentSortThread; // Reference to the currently running sorting thread
    private AtomicBoolean isSortRunning = new AtomicBoolean(false); // Atomic flag to check if a sort is active

    // --- Counters for Algorithm Analysis ---
    private volatile long swapCount = 0;
    private volatile long comparisonCount = 0;

    // --- Visualizer Panel ---
    private VisualizerPanel visualizerPanel;

    // --- Constructor ---
    public SortingVisualizer() {
        setTitle("Sorting Visualizer");
        // Ensure that ELEMENT_WIDTH is calculated based on the initial ARRAY_SIZE
        ELEMENT_WIDTH = FRAME_WIDTH / ARRAY_SIZE;

        // Set frame size and default close operation
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true); // Allow resizing to adjust element width dynamically

        // Initialize the array with random values
        array = generateRandomArray(ARRAY_SIZE);

        // Create and add the panel where bars are drawn
        visualizerPanel = new VisualizerPanel();
        add(visualizerPanel, BorderLayout.CENTER);

        // --- Control Panel Setup ---
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10)); // Layout for controls

        // Algorithm Selection
        algorithmComboBox = new JComboBox<>(new String[]{"Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort", "Quick Sort", "Heap Sort"});
        algorithmComboBox.addActionListener(e -> {
            String selectedAlgorithm = (String) algorithmComboBox.getSelectedItem();
            if (selectedAlgorithm != null) {
                resetCounters(); // Reset counters on algorithm change
                runSortingAlgorithm(selectedAlgorithm); // Start the selected sorting algorithm
            }
        });
        controlPanel.add(new JLabel("Algorithm:"));
        controlPanel.add(algorithmComboBox);

        // Restart Button
        restartButton = new JButton("Restart");
        restartButton.addActionListener(e -> {
            stopCurrentSortThread(); // Stop any running sort
            array = generateRandomArray(ARRAY_SIZE); // Generate a new random array
            resetCounters(); // Reset counters
            resetHighlighting(); // Reset highlighting indices
            isSorted = false; // Reset sorted flag
            visualizerPanel.repaint(); // Repaint to show new unsorted array
            // Optional: Auto-start the current algorithm or wait for user to select again
            runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
        });
        controlPanel.add(restartButton);

        // Pause/Resume Button
        pauseResumeButton = new JButton("Pause");
        pauseResumeButton.addActionListener(e -> {
            togglePause(); // Toggle pause state
        });
        pauseResumeButton.setEnabled(false); // Disable until a sort starts
        controlPanel.add(pauseResumeButton);

        // Speed Control Slider
        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 200, DELAY_MS); // Min 1ms, Max 200ms
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.addChangeListener(e -> {
            DELAY_MS = speedSlider.getValue(); // Update delay based on slider value
        });
        controlPanel.add(new JLabel("Speed (ms):"));
        controlPanel.add(speedSlider);

        // Array Size Slider
        arraySizeSlider = new JSlider(JSlider.HORIZONTAL, 10, 200, ARRAY_SIZE); // Min 10, Max 200 elements
        arraySizeSlider.setMajorTickSpacing(50);
        arraySizeSlider.setMinorTickSpacing(10);
        arraySizeSlider.setPaintTicks(true);
        arraySizeSlider.setPaintLabels(true);
        arraySizeSlider.addChangeListener(e -> {
            int newSize = arraySizeSlider.getValue();
            if (newSize != ARRAY_SIZE) {
                ARRAY_SIZE = newSize; // Update array size
                ELEMENT_WIDTH = FRAME_WIDTH / ARRAY_SIZE; // Recalculate bar width
                stopCurrentSortThread(); // Stop current sort
                array = generateRandomArray(ARRAY_SIZE); // Generate new array
                resetCounters(); // Reset counters
                resetHighlighting(); // Reset highlighting
                isSorted = false; // Reset sorted flag
                visualizerPanel.repaint(); // Repaint with new array size
                // Optional: Auto-start the current algorithm or wait for user
                runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
            }
        });
        controlPanel.add(new JLabel("Array Size:"));
        controlPanel.add(arraySizeSlider);

        // Algorithm Info Label
        algorithmInfoLabel = new JLabel("Algorithm Info: Not running");
        controlPanel.add(algorithmInfoLabel);

        // Counters
        swapCountLabel = new JLabel("Swaps: 0");
        controlPanel.add(swapCountLabel);
        comparisonCountLabel = new JLabel("Comparisons: 0");
        controlPanel.add(comparisonCountLabel);

        add(controlPanel, BorderLayout.NORTH); // Add control panel to the top of the frame

        pack(); // Packs components tightly
        setLocationRelativeTo(null); // Center the frame on screen
        setVisible(true);

        // Initial run to display the first algorithm
        runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
    }

    // --- Helper Methods ---

    /**
     * Generates a new array with random integer values.
     * The height of bars is scaled to fit within the frame height.
     * @param size The desired size of the array.
     * @return A new array with random values.
     */
    private int[] generateRandomArray(int size) {
        int[] arr = new int[size];
        Random rand = new Random();
        for (int i = 0; i < size; i++) {
            // Random heights between 10 and FRAME_HEIGHT - 30 to avoid going off screen or being too small
            arr[i] = rand.nextInt(FRAME_HEIGHT - 40) + 10;
        }
        return arr;
    }

    /**
     * Resets all highlighting indices to their default non-highlighted state.
     */
    private void resetHighlighting() {
        comparingIndex1 = -1;
        comparingIndex2 = -1;
        swappingIndex1 = -1;
        swappingIndex2 = -1;
        pivotIndex = -1;
        sortedElementBoundary = -1;
    }

    /**
     * Resets swap and comparison counters and updates their labels.
     */
    private void resetCounters() {
        swapCount = 0;
        comparisonCount = 0;
        SwingUtilities.invokeLater(() -> {
            swapCountLabel.setText("Swaps: 0");
            comparisonCountLabel.setText("Comparisons: 0");
        });
    }

    /**
     * Toggles the pause state of the visualization.
     */
    private void togglePause() {
        isPaused = !isPaused; // Invert the pause state
        if (isPaused) {
            pauseResumeButton.setText("Resume");
        } else {
            pauseResumeButton.setText("Pause");
            synchronized (pauseLock) {
                pauseLock.notifyAll(); // Notify all waiting threads to resume
            }
        }
    }

    /**
     * Stops the currently running sorting thread, if any.
     */
    private void stopCurrentSortThread() {
        if (currentSortThread != null && currentSortThread.isAlive()) {
            currentSortThread.interrupt(); // Request thread to stop
            try {
                currentSortThread.join(100); // Wait briefly for it to terminate
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for sort thread to finish.");
            }
        }
        isSortRunning.set(false); // Mark sort as not running
        resetHighlighting(); // Ensure no lingering highlights
        isSorted = false; // Reset sorted flag
        visualizerPanel.repaint(); // Repaint to clear highlights
        enableControls(true); // Re-enable controls
    }

    /**
     * Enables or disables UI controls during sorting.
     * @param enable True to enable, false to disable.
     */
    private void enableControls(boolean enable) {
        algorithmComboBox.setEnabled(enable);
        restartButton.setEnabled(enable);
        arraySizeSlider.setEnabled(enable);
        // Pause/Resume button state depends on sort running or not
        pauseResumeButton.setEnabled(!enable || isSortRunning.get());
    }

    /**
     * Starts the selected sorting algorithm in a new thread.
     * @param algorithm The name of the algorithm to run.
     */
    private void runSortingAlgorithm(String algorithm) {
        stopCurrentSortThread(); // Ensure previous sort is stopped
        array = generateRandomArray(ARRAY_SIZE); // Always start with a fresh array
        resetCounters();
        resetHighlighting();
        isSorted = false;
        visualizerPanel.repaint(); // Show the new unsorted array

        enableControls(false); // Disable controls while sorting
        isSortRunning.set(true);
        pauseResumeButton.setEnabled(true); // Enable pause button

        currentSortThread = new Thread(() -> {
            try {
                // Set algorithm info label
                SwingUtilities.invokeLater(() -> {
                    switch (algorithm) {
                        case "Bubble Sort": algorithmInfoLabel.setText("Bubble Sort: O(n^2) time, O(1) space"); break;
                        case "Selection Sort": algorithmInfoLabel.setText("Selection Sort: O(n^2) time, O(1) space"); break;
                        case "Insertion Sort": algorithmInfoLabel.setText("Insertion Sort: O(n^2) time, O(1) space"); break;
                        case "Merge Sort": algorithmInfoLabel.setText("Merge Sort: O(n log n) time, O(n) space"); break;
                        case "Quick Sort": algorithmInfoLabel.setText("Quick Sort: O(n log n) avg, O(n^2) worst time, O(log n) space"); break;
                        case "Heap Sort": algorithmInfoLabel.setText("Heap Sort: O(n log n) time, O(1) space"); break;
                        default: algorithmInfoLabel.setText("Algorithm Info: Not running"); break;
                    }
                });

                switch (algorithm) {
                    case "Bubble Sort": runBubbleSort(); break;
                    case "Selection Sort": runSelectionSort(); break;
                    case "Insertion Sort": runInsertionSort(); break;
                    case "Merge Sort": runMergeSort(array, 0, ARRAY_SIZE - 1); break;
                    case "Quick Sort": runQuickSort(array, 0, ARRAY_SIZE - 1); break;
                    case "Heap Sort": runHeapSort(array); break;
                }
                isSorted = true; // Mark as sorted after completion
            } catch (InterruptedException e) {
                System.out.println("Sorting interrupted: " + algorithm);
            } finally {
                resetHighlighting(); // Clear any lingering highlights
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint()); // Final repaint for sorted state
                isSortRunning.set(false); // Mark sort as finished
                enableControls(true); // Re-enable controls
                pauseResumeButton.setText("Pause"); // Reset pause button text
                pauseResumeButton.setEnabled(false); // Disable pause button after sort
            }
        });
        currentSortThread.start();
    }

    /**
     * Swaps two elements in the array and updates swap count.
     * @param index1 First index.
     * @param index2 Second index.
     * @throws InterruptedException if thread is interrupted during sleep.
     */
    private void swap(int index1, int index2) throws InterruptedException {
        swappingIndex1 = index1;
        swappingIndex2 = index2;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint()); // Repaint with swap highlight
        sleep();

        int temp = array[index1];
        array[index1] = array[index2];
        array[index2] = temp;

        swapCount++;
        SwingUtilities.invokeLater(() -> swapCountLabel.setText("Swaps: " + swapCount));

        // No need for a sleep after the actual swap if repaint follows in algorithm
        // But ensures highlight is visible before returning to normal
        swappingIndex1 = -1;
        swappingIndex2 = -1;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint()); // Repaint to clear swap highlight
    }

    /**
     * Pauses the thread if `isPaused` is true, then sleeps for `DELAY_MS`.
     * @throws InterruptedException if the thread is interrupted.
     */
    private void sleep() throws InterruptedException {
        // Check for interruption request
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        // Handle pause
        synchronized (pauseLock) {
            while (isPaused) {
                pauseLock.wait(); // Wait until notified to resume
                if (Thread.currentThread().isInterrupted()) { // Check interruption after waking up
                    throw new InterruptedException();
                }
            }
        }

        // Actual delay
        Thread.sleep(DELAY_MS);
    }

    // --- Sorting Algorithm Implementations (Modified for Visualization) ---

    private void runBubbleSort() throws InterruptedException {
        for (int i = 0; i < ARRAY_SIZE - 1; i++) {
            for (int j = 0; j < ARRAY_SIZE - 1 - i; j++) {
                comparisonCount++;
                SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

                comparingIndex1 = j;
                comparingIndex2 = j + 1;
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep(); // Pause for comparison highlight

                if (array[j] > array[j + 1]) {
                    swap(j, j + 1);
                }
                resetHighlighting(); // Clear comparison/swap highlights
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep(); // Small delay to show state after potential swap
            }
            sortedElementBoundary = ARRAY_SIZE - 1 - i; // Mark elements as sorted
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        }
        sortedElementBoundary = 0; // All elements sorted
    }

    private void runSelectionSort() throws InterruptedException {
        for (int i = 0; i < ARRAY_SIZE - 1; i++) {
            int minIndex = i;
            comparingIndex1 = i; // Highlight current element being considered for minimum
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            for (int j = i + 1; j < ARRAY_SIZE; j++) {
                comparisonCount++;
                SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

                comparingIndex2 = j; // Highlight element being compared against current minimum
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();

                if (array[j] < array[minIndex]) {
                    minIndex = j;
                    comparingIndex1 = minIndex; // Update highlight to new minimum
                    SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                    sleep();
                }
            }
            if (minIndex != i) {
                swap(i, minIndex);
            }
            sortedElementBoundary = i; // Mark current element as sorted
            resetHighlighting(); // Clear comparisons
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
        }
        sortedElementBoundary = ARRAY_SIZE; // All elements sorted
    }

    private void runInsertionSort() throws InterruptedException {
        for (int i = 1; i < ARRAY_SIZE; i++) {
            int key = array[i];
            int j = i - 1;

            comparingIndex1 = i; // Element to be inserted
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            while (j >= 0) {
                comparisonCount++;
                SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

                comparingIndex2 = j; // Element being compared against
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();

                if (array[j] > key) {
                    array[j + 1] = array[j];
                    SwingUtilities.invokeLater(() -> visualizerPanel.repaint()); // Shift visualization
                    sleep();
                    j--;
                } else {
                    break;
                }
            }
            array[j + 1] = key;
            resetHighlighting(); // Clear comparisons
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep(); // Show final position after insertion
        }
    }

    private void runMergeSort(int[] arr, int left, int right) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (left < right) {
            int mid = (left + right) / 2;

            runMergeSort(arr, left, mid);
            runMergeSort(arr, mid + 1, right);

            merge(arr, left, mid, right);
        }
    }

    private void merge(int[] arr, int left, int mid, int right) throws InterruptedException {
        int n1 = mid - left + 1;
        int n2 = right - mid;

        int[] leftArr = new int[n1];
        int[] rightArr = new int[n2];

        System.arraycopy(arr, left, leftArr, 0, n1);
        System.arraycopy(arr, mid + 1, rightArr, 0, n2);

        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            comparisonCount++;
            SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

            comparingIndex1 = left + i;
            comparingIndex2 = mid + 1 + j;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            if (leftArr[i] <= rightArr[j]) {
                arr[k] = leftArr[i];
                i++;
            } else {
                arr[k] = rightArr[j];
                j++;
            }
            k++;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint()); // Repaint after element placement
            sleep();
        }

        while (i < n1) {
            arr[k] = leftArr[i];
            comparingIndex1 = left + i; // Highlight element being copied
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
            i++;
            k++;
        }

        while (j < n2) {
            arr[k] = rightArr[j];
            comparingIndex2 = mid + 1 + j; // Highlight element being copied
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
            j++;
            k++;
        }
        resetHighlighting();
    }

    private void runQuickSort(int[] arr, int low, int high) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (low < high) {
            int pi = partition(arr, low, high);
            runQuickSort(arr, low, pi - 1);
            runQuickSort(arr, pi + 1, high);
        }
    }

    private int partition(int[] arr, int low, int high) throws InterruptedException {
        int pivot = arr[high];
        pivotIndex = high; // Highlight pivot
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        int i = low - 1;

        for (int j = low; j < high; j++) {
            comparisonCount++;
            SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

            comparingIndex1 = j; // Highlight element being compared against pivot
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            if (arr[j] < pivot) {
                i++;
                if (i != j) { // Only swap if different elements
                    swap(i, j);
                }
                resetHighlighting();
                pivotIndex = high; // Keep pivot highlighted
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();
            }
        }

        if (i + 1 != high) { // Only swap if pivot needs to move
            swap(i + 1, high);
        }
        resetHighlighting();
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep(); // Show final pivot position
        return i + 1;
    }

    private void runHeapSort(int[] arr) throws InterruptedException {
        int n = arr.length;

        // Build heap (rearrange array)
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }

        // One by one extract an element from heap
        for (int i = n - 1; i >= 0; i--) {
            // Move current root to end
            swap(0, i);
            sortedElementBoundary = i; // Mark this element as sorted
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            // call max heapify on the reduced heap
            heapify(arr, i, 0);
        }
        sortedElementBoundary = 0; // All elements sorted
    }

    private void heapify(int[] arr, int n, int i) throws InterruptedException {
        int largest = i; // Initialize largest as root
        int left = 2 * i + 1; // left child
        int right = 2 * i + 2; // right child

        comparisonCount++; SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));
        comparingIndex1 = largest; // Highlight current root
        comparingIndex2 = -1; // No second comparison element initially
        if (left < n) comparingIndex2 = left; // Highlight left child if exists
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        // If left child is larger than root
        if (left < n && arr[left] > arr[largest]) {
            largest = left;
        }

        comparisonCount++; SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));
        comparingIndex1 = largest; // New potential largest
        comparingIndex2 = -1;
        if (right < n) comparingIndex2 = right; // Highlight right child if exists
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        // If right child is larger than largest so far
        if (right < n && arr[right] > arr[largest]) {
            largest = right;
        }

        // If largest is not root
        if (largest != i) {
            swap(i, largest);
            // Recursively heapify the affected sub-tree
            heapify(arr, n, largest);
        }
        resetHighlighting(); // Clear comparisons after heapify step
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();
    }

    // --- Inner Class for Visualization Panel ---
    private class VisualizerPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.clearRect(0, 0, getWidth(), getHeight()); // Clear the panel background

            for (int i = 0; i < ARRAY_SIZE; i++) {
                int barHeight = array[i];
                int x = i * ELEMENT_WIDTH;
                int y = getHeight() - barHeight; // Draw bars from the bottom

                // Set default bar color
                g2d.setColor(Color.BLUE);

                // --- Apply highlighting colors based on state ---
                if (isSorted) {
                    g2d.setColor(Color.LIGHT_GRAY); // Fully sorted
                } else if (i == sortedElementBoundary && (algorithmComboBox.getSelectedItem().equals("Bubble Sort") || algorithmComboBox.getSelectedItem().equals("Selection Sort") || algorithmComboBox.getSelectedItem().equals("Heap Sort"))) {
                     g2d.setColor(Color.GREEN.darker()); // Darker green for sorted elements
                } else if (i == pivotIndex) {
                    g2d.setColor(Color.MAGENTA); // Pivot element
                } else if (i == swappingIndex1 || i == swappingIndex2) {
                    g2d.setColor(Color.RED); // Swapping elements
                } else if (i == comparingIndex1 || i == comparingIndex2) {
                    g2d.setColor(Color.YELLOW); // Comparing elements
                } else if (i >= sortedElementBoundary && sortedElementBoundary != -1 && (algorithmComboBox.getSelectedItem().equals("Merge Sort") || algorithmComboBox.getSelectedItem().equals("Quick Sort"))) {
                    // For algorithms where sorted elements are not explicitly marked from one end
                    // This might need more nuanced logic depending on how you want to show 'sorted' parts for recursive sorts.
                    // For now, this is a placeholder.
                    // g2d.setColor(Color.GRAY);
                }


                g2d.fillRect(x, y, ELEMENT_WIDTH - 1, barHeight); // Fill bar, -1 for gap
                g2d.setColor(Color.BLACK); // Border color
                g2d.drawRect(x, y, ELEMENT_WIDTH - 1, barHeight); // Draw border
            }
        }
    }

    // --- Main Method ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(SortingVisualizer::new); // Run GUI on Event Dispatch Thread
    }
}