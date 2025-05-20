import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

public class SortingVisualizer extends JFrame {
    private static final int ARRAY_SIZE = 50;
    private static final int ELEMENT_WIDTH = 25;
    private static final int FRAME_WIDTH = 1500;
    private static final int FRAME_HEIGHT = 500;
    private static final int DELAY_MS = 50;

    private int[] array;

    private JComboBox<String> algorithmComboBox;
    private JButton restartButton;

    public SortingVisualizer() {
        setTitle("Sorting Visualizer");
        setSize(FRAME_WIDTH, FRAME_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        array = generateRandomArray(ARRAY_SIZE);

        VisualizerPanel visualizerPanel = new VisualizerPanel();
        add(visualizerPanel);

        algorithmComboBox = new JComboBox<>(new String[]{"Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort", "Quick Sort", "Heap Sort"});
        algorithmComboBox.addActionListener(e -> {
            String selectedAlgorithm = (String) algorithmComboBox.getSelectedItem();
            if (selectedAlgorithm != null) {
                runSortingAlgorithm(selectedAlgorithm);
            }
        });

        restartButton = new JButton("Restart");
         restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                array = generateRandomArray(ARRAY_SIZE);
                repaint();
                runSortingAlgorithm((String) algorithmComboBox.getSelectedItem());
            }
        });

        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("Select Sorting Algorithm: "));
        controlPanel.add(algorithmComboBox);
        controlPanel.add(restartButton);

        add(controlPanel, BorderLayout.NORTH);

        pack();
        setLocationRelativeTo(null); // Center the frame
        setVisible(true);
    }

    private int[] generateRandomArray(int size) {
        int[] arr = new int[size];
        Random rand = new Random();
        for (int i = 0; i < size; i++) {
            arr[i] = rand.nextInt(FRAME_HEIGHT - 20) + 10; // Random heights within frame height
        }
        return arr;
    }

    private void runSortingAlgorithm(String algorithm) {
        switch (algorithm) {
            case "Bubble Sort":
                runBubbleSort();
                break;
            case "Selection Sort":
                runSelectionSort();
                break;
            case "Insertion Sort":
                runInsertionSort();
                break;
            case "Merge Sort":
                runMergeSort();
                break;
            case "Quick Sort":
                runQuickSort();
                break;
            case "Heap Sort":
                runHeapSort();
                break;
            default:
                break;
        }
    }

    private void runBubbleSort() {
        new Thread(() -> {
            for (int i = 0; i < ARRAY_SIZE - 1; i++) {
                for (int j = 0; j < ARRAY_SIZE - 1 - i; j++) {
                    if (array[j] > array[j + 1]) {
                        swap(j, j + 1);
                        repaint();
                        sleep();
                    }
                }
            }
        }).start();
    }

    private void runSelectionSort() {
        new Thread(() -> {
            for (int i = 0; i < ARRAY_SIZE - 1; i++) {
                int minIndex = i;
                for (int j = i + 1; j < ARRAY_SIZE; j++) {
                    if (array[j] < array[minIndex]) {
                        minIndex = j;
                    }
                }
                swap(i, minIndex);
                repaint();
                sleep();
            }
        }).start();
    }

    private void runInsertionSort() {
        new Thread(() -> {
            for (int i = 1; i < ARRAY_SIZE; i++) {
                int key = array[i];
                int j = i - 1;
                while (j >= 0 && array[j] > key) {
                    array[j + 1] = array[j];
                    j--;
                    repaint();
                    sleep();
                }
                array[j + 1] = key;
            }
        }).start();
    }

    private void runMergeSort() {
        new Thread(() -> mergeSort(array, 0, ARRAY_SIZE - 1)).start();
    }

    private void mergeSort(int[] arr, int left, int right) {
        if (left < right) {
            int mid = (left + right) / 2;

            mergeSort(arr, left, mid);
            mergeSort(arr, mid + 1, right);

            merge(arr, left, mid, right);
        }
    }

    private void merge(int[] arr, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;

        int[] leftArr = new int[n1];
        int[] rightArr = new int[n2];

        System.arraycopy(arr, left, leftArr, 0, n1);
        System.arraycopy(arr, mid + 1, rightArr, 0, n2);

        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            if (leftArr[i] <= rightArr[j]) {
                arr[k] = leftArr[i];
                repaint();
                sleep();
                i++;
            } else {
                arr[k] = rightArr[j];
                repaint();
                sleep();
                j++;
            }
            k++;
        }

        while (i < n1) {
            arr[k] = leftArr[i];
            repaint();
            sleep();
            i++;
            k++;
        }

        while (j < n2) {
            arr[k] = rightArr[j];
            repaint();
            sleep();
            j++;
            k++;
        }
    }

    private void runQuickSort() {
        new Thread(() -> quickSort(array, 0, ARRAY_SIZE - 1)).start();
    }

    private void quickSort(int[] arr, int low, int high) {
        if (low < high) {
            int pi = partition(arr, low, high);

            quickSort(arr, low, pi - 1);
            quickSort(arr, pi + 1, high);
        }
    }

    private int partition(int[] arr, int low, int high) {
        int pivot = arr[high];
        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (arr[j] < pivot) {
                i++;
                swap(i, j);
                repaint();
                sleep();
            }
        }

        swap(i + 1, high);
        repaint();
        sleep();
        return i + 1;
    }

    private void runHeapSort() {
        new Thread(() -> heapSort(array)).start();
    }

    private void heapSort(int[] arr) {
        int n = arr.length;

        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }

        for (int i = n - 1; i >= 0; i--) {
            swap(0, i);
            repaint();
            sleep();

            heapify(arr, i, 0);
        }
    }

    private void heapify(int[] arr, int n, int i) {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;

        if (left < n && arr[left] > arr[largest]) {
            largest = left;
        }

        if (right < n && arr[right] > arr[largest]) {
            largest = right;
        }

        if (largest != i) {
            swap(i, largest);
            repaint();
            sleep();

            heapify(arr, n, largest);
        }
    }

    private void swap(int index1, int index2) {
        int temp = array[index1];
        array[index1] = array[index2];
        array[index2] = temp;
    }

    private void sleep() {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class VisualizerPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int i = 0; i < ARRAY_SIZE; i++) {
                int barHeight = array[i];
                int x = i * ELEMENT_WIDTH;
                int y = FRAME_HEIGHT - barHeight;
                g.fillRect(x, y, ELEMENT_WIDTH, barHeight);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SortingVisualizer::new);
    }
}
