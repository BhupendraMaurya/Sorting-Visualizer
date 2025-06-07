import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class SortingVisualizer extends JFrame {
    // --- Constants and Volatile Variables for UI/Algorithm State ---
    private volatile int ARRAY_SIZE = 50;
    private volatile int ELEMENT_WIDTH;
    private static final int FRAME_WIDTH = 1000;
    private static final int FRAME_HEIGHT = 500;
    private volatile int DELAY_MS = 50;

    private int[] array;

    // UI Components
    private JComboBox<String> algorithmComboBox;
    private JButton restartButton;
    private JButton pauseResumeButton;
    private JSlider speedSlider;
    private JSlider arraySizeSlider;
    private JLabel algorithmInfoLabel;
    private JLabel swapCountLabel;
    private JLabel comparisonCountLabel;
    private JPanel controlPanel; // <--- MAKE controlPanel an instance variable

    // --- State Variables for Visualization ---
    private volatile int comparingIndex1 = -1;
    private volatile int comparingIndex2 = -1;
    private volatile int swappingIndex1 = -1;
    private volatile int swappingIndex2 = -1;
    private volatile int pivotIndex = -1;
    private volatile int sortedElementBoundary = -1;
    private volatile boolean isSorted = false;

    // --- Thread Control Variables ---
    private volatile boolean isPaused = false;
    private final Object pauseLock = new Object();
    private Thread currentSortThread;
    private AtomicBoolean isSortRunning = new AtomicBoolean(false);

    // --- Counters for Algorithm Analysis ---
    private volatile long swapCount = 0;
    private volatile long comparisonCount = 0;

    // --- Visualizer Panel ---
    private VisualizerPanel visualizerPanel;

    // --- Constructor ---
    public SortingVisualizer() {
        setTitle("Sorting Visualizer");

        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);

        visualizerPanel = new VisualizerPanel();
        add(visualizerPanel, BorderLayout.CENTER);

        // --- Initialize controlPanel here as an instance variable ---
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));

        // Initialize array AFTER visualizerPanel and controlPanel (for initial height calculation)
        array = generateRandomArray(ARRAY_SIZE);

        // Algorithm Selection
        algorithmComboBox = new JComboBox<>(new String[]{"Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort", "Quick Sort", "Heap Sort"});
        algorithmComboBox.addActionListener(e -> {
            String selectedAlgorithm = (String) algorithmComboBox.getSelectedItem();
            if (selectedAlgorithm != null) {
                resetCounters();
                runSortingAlgorithm(selectedAlgorithm);
            }
        });
        controlPanel.add(new JLabel("Algorithm:"));
        controlPanel.add(algorithmComboBox);

        // Restart Button
        restartButton = new JButton("Restart");
        restartButton.addActionListener(e -> {
            stopCurrentSortThread();
            array = generateRandomArray(ARRAY_SIZE);
            resetCounters();
            resetHighlighting();
            isSorted = false;
            visualizerPanel.repaint();
            runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
        });
        controlPanel.add(restartButton);

        // Pause/Resume Button
        pauseResumeButton = new JButton("Pause");
        pauseResumeButton.addActionListener(e -> {
            togglePause();
        });
        pauseResumeButton.setEnabled(false);
        controlPanel.add(pauseResumeButton);

        // Speed Control Slider
        speedSlider = new JSlider(JSlider.HORIZONTAL, 1, 200, DELAY_MS);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(10);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.addChangeListener(e -> {
            DELAY_MS = speedSlider.getValue();
        });
        controlPanel.add(new JLabel("Speed (ms):"));
        controlPanel.add(speedSlider);

        // Array Size Slider
        arraySizeSlider = new JSlider(JSlider.HORIZONTAL, 10, 200, ARRAY_SIZE);
        arraySizeSlider.setMajorTickSpacing(50);
        arraySizeSlider.setMinorTickSpacing(10);
        arraySizeSlider.setPaintTicks(true);
        arraySizeSlider.setPaintLabels(true);
        arraySizeSlider.addChangeListener(e -> {
            int newSize = arraySizeSlider.getValue();
            if (newSize != ARRAY_SIZE) {
                ARRAY_SIZE = newSize;
                ELEMENT_WIDTH = visualizerPanel.getWidth() / ARRAY_SIZE;

                stopCurrentSortThread();
                array = generateRandomArray(ARRAY_SIZE);
                resetCounters();
                resetHighlighting();
                isSorted = false;
                visualizerPanel.repaint();
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

        add(controlPanel, BorderLayout.NORTH);

        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setVisible(true);

        visualizerPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                ELEMENT_WIDTH = visualizerPanel.getWidth() / ARRAY_SIZE;
                array = generateRandomArray(ARRAY_SIZE);
                visualizerPanel.repaint();
            }
        });

        runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
    }

    // --- Helper Methods ---

    private int[] generateRandomArray(int size) {
        int[] arr = new int[size];
        Random rand = new Random();

        // Get actual height of the visualizer panel for bar scaling
        int panelHeight = visualizerPanel.getHeight();

        // Fallback if panelHeight is 0 (e.g., still during initial layout)
        if (panelHeight <= 0) {
             // A more accurate initial fallback for the visual area
             panelHeight = FRAME_HEIGHT - (controlPanel != null ? controlPanel.getPreferredSize().height : 0);
             if (panelHeight <= 0) panelHeight = FRAME_HEIGHT; // Final safety fallback
        }

        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(panelHeight - 40) + 10;
        }
        return arr;
    }

    private void resetHighlighting() {
        comparingIndex1 = -1;
        comparingIndex2 = -1;
        swappingIndex1 = -1;
        swappingIndex2 = -1;
        pivotIndex = -1;
        sortedElementBoundary = -1;
    }

    private void resetCounters() {
        swapCount = 0;
        comparisonCount = 0;
        SwingUtilities.invokeLater(() -> {
            swapCountLabel.setText("Swaps: 0");
            comparisonCountLabel.setText("Comparisons: 0");
        });
    }

    private void togglePause() {
        isPaused = !isPaused;
        if (isPaused) {
            pauseResumeButton.setText("Resume");
        } else {
            pauseResumeButton.setText("Pause");
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    private void stopCurrentSortThread() {
        if (currentSortThread != null && currentSortThread.isAlive()) {
            currentSortThread.interrupt();
            try {
                currentSortThread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while waiting for sort thread to finish.");
            }
        }
        isSortRunning.set(false);
        resetHighlighting();
        isSorted = false;
        enableControls(true);
    }

    private void enableControls(boolean enable) {
        algorithmComboBox.setEnabled(enable);
        restartButton.setEnabled(enable);
        arraySizeSlider.setEnabled(enable);
        pauseResumeButton.setEnabled(!enable || isSortRunning.get());
    }

    private void runSortingAlgorithm(String algorithm) {
        stopCurrentSortThread();
        array = generateRandomArray(ARRAY_SIZE);
        resetCounters();
        resetHighlighting();
        isSorted = false;
        visualizerPanel.repaint();

        enableControls(false);
        isSortRunning.set(true);
        pauseResumeButton.setEnabled(true);

        currentSortThread = new Thread(() -> {
            try {
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
                isSorted = true;
            } catch (InterruptedException e) {
                System.out.println("Sorting interrupted: " + algorithm);
            } finally {
                resetHighlighting();
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                isSortRunning.set(false);
                enableControls(true);
                pauseResumeButton.setText("Pause");
                pauseResumeButton.setEnabled(false);
            }
        });
        currentSortThread.start();
    }

    private void swap(int index1, int index2) throws InterruptedException {
        swappingIndex1 = index1;
        swappingIndex2 = index2;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        int temp = array[index1];
        array[index1] = array[index2];
        array[index2] = temp;

        swapCount++;
        SwingUtilities.invokeLater(() -> swapCountLabel.setText("Swaps: " + swapCount));

        swappingIndex1 = -1;
        swappingIndex2 = -1;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
    }

    private void sleep() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        synchronized (pauseLock) {
            while (isPaused) {
                pauseLock.wait();
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
            }
        }

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
                sleep();

                if (array[j] > array[j + 1]) {
                    swap(j, j + 1);
                }
                resetHighlighting();
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            }
            sortedElementBoundary = ARRAY_SIZE - 1 - i;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        }
        sortedElementBoundary = -1;
    }

    private void runSelectionSort() throws InterruptedException {
        for (int i = 0; i < ARRAY_SIZE - 1; i++) {
            int minIndex = i;

            for (int j = i + 1; j < ARRAY_SIZE; j++) {
                comparisonCount++;
                SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

                comparingIndex1 = minIndex;
                comparingIndex2 = j;
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();

                if (array[j] < array[minIndex]) {
                    minIndex = j;
                }
            }
            if (minIndex != i) {
                swap(i, minIndex);
            }
            sortedElementBoundary = i;
            resetHighlighting();
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        }
        sortedElementBoundary = -1;
    }

    private void runInsertionSort() throws InterruptedException {
        for (int i = 1; i < ARRAY_SIZE; i++) {
            int key = array[i];
            int j = i - 1;

            comparingIndex1 = i;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            while (j >= 0) {
                comparisonCount++;
                SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

                comparingIndex2 = j;
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();

                if (array[j] > key) {
                    array[j + 1] = array[j];
                    swappingIndex1 = j;
                    swappingIndex2 = j + 1;
                    SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                    sleep();
                    swappingIndex1 = -1;
                    swappingIndex2 = -1;
                    j--;
                } else {
                    break;
                }
            }
            array[j + 1] = key;
            resetHighlighting();
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
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
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
        }

        while (i < n1) {
            arr[k] = leftArr[i];
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();
            i++;
            k++;
        }

        while (j < n2) {
            arr[k] = rightArr[j];
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
            resetHighlighting();
            runQuickSort(arr, low, pi - 1);
            runQuickSort(arr, pi + 1, high);
        }
    }

    private int partition(int[] arr, int low, int high) throws InterruptedException {
        int pivot = arr[high];
        pivotIndex = high;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        int i = low - 1;

        for (int j = low; j < high; j++) {
            comparisonCount++;
            SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));

            comparingIndex1 = j;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            if (arr[j] < pivot) {
                i++;
                if (i != j) {
                    swap(i, j);
                }
                resetHighlighting();
                pivotIndex = high;
                SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
                sleep();
            }
        }

        if (i + 1 != high) {
            swap(i + 1, high);
        }
        pivotIndex = -1;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();
        return i + 1;
    }

    private void runHeapSort(int[] arr) throws InterruptedException {
        int n = arr.length;

        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }

        for (int i = n - 1; i >= 0; i--) {
            swap(0, i);
            sortedElementBoundary = i;
            SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
            sleep();

            heapify(arr, i, 0);
        }
        sortedElementBoundary = -1;
    }

    private void heapify(int[] arr, int n, int i) throws InterruptedException {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;

        comparisonCount++;
        SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));
        comparingIndex1 = largest;
        comparingIndex2 = -1;
        if (left < n) comparingIndex2 = left;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        if (left < n && arr[left] > arr[largest]) {
            largest = left;
        }

        comparisonCount++;
        SwingUtilities.invokeLater(() -> comparisonCountLabel.setText("Comparisons: " + comparisonCount));
        comparingIndex1 = largest;
        comparingIndex2 = -1;
        if (right < n) comparingIndex2 = right;
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();

        if (right < n && arr[right] > arr[largest]) {
            largest = right;
        }

        if (largest != i) {
            swap(i, largest);
            heapify(arr, n, largest);
        }
        resetHighlighting();
        SwingUtilities.invokeLater(() -> visualizerPanel.repaint());
        sleep();
    }

    private class VisualizerPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.clearRect(0, 0, getWidth(), getHeight());

            ELEMENT_WIDTH = getWidth() / ARRAY_SIZE; // Ensure ELEMENT_WIDTH is always calculated correctly

            for (int i = 0; i < ARRAY_SIZE; i++) {
                int barHeight = array[i];
                int x = i * ELEMENT_WIDTH;
                int y = getHeight() - barHeight;

                g2d.setColor(Color.BLUE);

                if (isSorted) {
                    g2d.setColor(Color.LIGHT_GRAY);
                } else if (i < sortedElementBoundary || (algorithmComboBox.getSelectedItem() != null && algorithmComboBox.getSelectedItem().equals("Selection Sort") && i <= sortedElementBoundary)) {
                    g2d.setColor(Color.GREEN.darker());
                } else if (i >= sortedElementBoundary && sortedElementBoundary != -1 && algorithmComboBox.getSelectedItem() != null && algorithmComboBox.getSelectedItem().equals("Bubble Sort")) {
                    g2d.setColor(Color.GREEN.darker());
                } else if (i >= sortedElementBoundary && sortedElementBoundary != -1 && algorithmComboBox.getSelectedItem() != null && algorithmComboBox.getSelectedItem().equals("Heap Sort")) {
                    g2d.setColor(Color.GREEN.darker());
                }
                else if (i == pivotIndex) {
                    g2d.setColor(Color.MAGENTA);
                } else if (i == swappingIndex1 || i == swappingIndex2) {
                    g2d.setColor(Color.RED);
                } else if (i == comparingIndex1 || i == comparingIndex2) {
                    g2d.setColor(Color.YELLOW);
                }

                g2d.fillRect(x, y, ELEMENT_WIDTH - 1, barHeight);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(x, y, ELEMENT_WIDTH - 1, barHeight);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SortingVisualizer::new);
    }
}