import sys
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QPushButton, QVBoxLayout,
    QWidget, QLabel, QFileDialog, QScrollArea, QHBoxLayout
)
from PyQt5.QtGui import QPixmap, QImage
from PyQt5.QtCore import Qt, QSize

import fitz # PyMuPDF

class PDFReader(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("PDFReader")
        self.setGeometry(100, 100, 800, 600) # x, y, width, height

        self.current_pdf_document = None
        self.current_page_number = 0
        self.zoom_factor = 1.5 # Default zoom for rendering (larger for better quality)

        self.init_ui()

    def init_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        main_layout = QVBoxLayout()
        central_widget.setLayout(main_layout)

        # Top Bar: Open Button
        top_bar_layout = QHBoxLayout()
        open_button = QPushButton("Open PDF")
        open_button.clicked.connect(self.open_pdf)
        top_bar_layout.addWidget(open_button)
        top_bar_layout.addStretch() # Pushes the button to the left
        main_layout.addLayout(top_bar_layout)

        # Page Display Area with Scroll
        self.image_label = QLabel()
        self.image_label.setAlignment(Qt.AlignCenter)
        self.image_label.setScaledContents(False) # Important: we scale the pixmap, not the label itself
        
        self.scroll_area = QScrollArea()
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setWidget(self.image_label)
        
        # Set a minimum size for the scroll area to ensure it's visible
        self.scroll_area.setMinimumSize(400, 400) 
        main_layout.addWidget(self.scroll_area)

        # Navigation Bar: Previous, Page Number, Next
        nav_layout = QHBoxLayout()
        self.prev_button = QPushButton("Previous")
        self.prev_button.clicked.connect(self.show_previous_page)
        self.prev_button.setEnabled(False) # Disable until PDF is loaded

        self.page_number_label = QLabel("Page: 0/0")
        self.page_number_label.setAlignment(Qt.AlignCenter)

        self.next_button = QPushButton("Next")
        self.next_button.clicked.connect(self.show_next_page)
        self.next_button.setEnabled(False) # Disable until PDF is loaded

        nav_layout.addWidget(self.prev_button)
        nav_layout.addStretch()
        nav_layout.addWidget(self.page_number_label)
        nav_layout.addStretch()
        nav_layout.addWidget(self.next_button)
        main_layout.addLayout(nav_layout)

        self.update_navigation_buttons() # Initial state

    def open_pdf(self):
        file_dialog = QFileDialog()
        file_path, _ = file_dialog.getOpenFileName(
            self, "Open PDF Document", "", "PDF Files (*.pdf);;All Files (*)"
        )
        if file_path:
            try:
                self.current_pdf_document = fitz.open(file_path)
                self.current_page_number = 0 # Reset to first page
                self.display_page(self.current_page_number)
                self.update_page_label()
                self.update_navigation_buttons()
            except Exception as e:
                # Basic error handling: print to console, could show a QMessageBox
                print(f"Error opening or reading PDF: {e}")
                self.current_pdf_document = None
                self.image_label.clear()
                self.update_page_label()
                self.update_navigation_buttons()
                
    def display_page(self, page_num):
        if self.current_pdf_document and 0 <= page_num < len(self.current_pdf_document):
            page = self.current_pdf_document[page_num]
            
            # Render page as a pixmap with zoom factor for better quality
            mat = fitz.Matrix(self.zoom_factor, self.zoom_factor)
            pix = page.get_pixmap(matrix=mat, alpha=False) # alpha=False for smaller size if no transparency needed

            # Convert pixmap to QImage
            # PyMuPDF's pix.samples is bytes, pix.stride is bytes per row
            img = QImage(pix.samples, pix.width, pix.height, pix.stride, QImage.Format_RGBX8888)
            
            # Convert QImage to QPixmap and set to label
            self.image_label.setPixmap(QPixmap.fromImage(img))
            
            self.current_page_number = page_num
        else:
            self.image_label.clear() # Clear display if no PDF or invalid page
            
    def show_next_page(self):
        if self.current_pdf_document and self.current_page_number < len(self.current_pdf_document) - 1:
            self.current_page_number += 1
            self.display_page(self.current_page_number)
            self.update_page_label()
            self.update_navigation_buttons()

    def show_previous_page(self):
        if self.current_pdf_document and self.current_page_number > 0:
            self.current_page_number -= 1
            self.display_page(self.current_page_number)
            self.update_page_label()
            self.update_navigation_buttons()

    def update_page_label(self):
        if self.current_pdf_document:
            total_pages = len(self.current_pdf_document)
            self.page_number_label.setText(f"Page: {self.current_page_number + 1}/{total_pages}")
        else:
            self.page_number_label.setText("Page: 0/0")

    def update_navigation_buttons(self):
        if self.current_pdf_document:
            total_pages = len(self.current_pdf_document)
            self.prev_button.setEnabled(self.current_page_number > 0)
            self.next_button.setEnabled(self.current_page_number < total_pages - 1)
        else:
            self.prev_button.setEnabled(False)
            self.next_button.setEnabled(False)

if __name__ == '__main__':
    app = QApplication(sys.argv)
    reader = PDFReader()
    reader.show()
    sys.exit(app.exec_()
