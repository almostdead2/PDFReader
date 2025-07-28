import sys
import os
import shutil # For temporary file handling

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.image import Image
from kivy.uix.label import Label
from kivy.properties import ObjectProperty, NumericProperty, StringProperty
from kivy.clock import mainthread # To update GUI from non-GUI threads

# PyMuPDF for PDF rendering
import fitz

# Pyjnius for Android-specific operations (handling Intents, accessing Java APIs)
try:
    from jnius import autoclass
    from android.storage import primary_external_storage_path # For temporary file path
    is_android = True
except ImportError:
    is_android = False
    print("Not running on Android environment. Pyjnius not available.")


class PDFReaderLayout(BoxLayout):
    pdf_display_image = ObjectProperty(None)
    page_info_label = ObjectProperty(None)
    status_label = ObjectProperty(None) # Added for user feedback

    current_pdf_document = None
    current_page_number = NumericProperty(0)
    total_pages = NumericProperty(0)
    zoom_factor = 2.0 # Higher for better quality

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.orientation = 'vertical'
        self.spacing = 5
        self.padding = 10

        # Status/Info Label at the top
        self.status_label = Label(text="Open a PDF via Sharing/Intent", size_hint_y=0.1)
        self.add_widget(self.status_label)

        # PDF Display Area (Image Widget)
        # Using a default image path for placeholder, will be updated dynamically
        self.pdf_display_image = Image(source='data/logo/kivy-icon-128.png', allow_stretch=True, keep_ratio=True)
        self.add_widget(self.pdf_display_image)

        # Navigation Bar
        nav_layout = BoxLayout(size_hint_y=0.1, spacing=10)
        
        self.prev_button = Button(text="Previous", size_hint_x=0.3)
        self.prev_button.bind(on_release=self.show_previous_page)
        self.prev_button.disabled = True

        self.page_info_label = Label(text="Page: 0/0", size_hint_x=0.4)

        self.next_button = Button(text="Next", size_hint_x=0.3)
        self.next_button.bind(on_release=self.show_next_page)
        self.next_button.disabled = True

        nav_layout.add_widget(self.prev_button)
        nav_layout.add_widget(self.page_info_label)
        nav_layout.add_widget(self.next_button)
        self.add_widget(nav_layout)

        # Register for Android Intent if on Android
        if is_android:
            self.setup_android_intent_receiver()
        else:
            self.status_label.text = "Not on Android. PDF sharing not supported."
            # For testing on desktop, you could add a local file picker for convenience
            # But remember, this is NOT how it will work on Android without permissions!
            desktop_open_btn = Button(text="Open PDF (Desktop Test)", size_hint_y=0.1)
            desktop_open_btn.bind(on_release=self.open_pdf_desktop_test)
            self.add_widget(desktop_open_btn)


    def setup_android_intent_receiver(self):
        PythonActivity = autoclass('org.kivy.android.PythonActivity')
        Intent = autoclass('android.content.Intent')
        
        # Get the current Intent that launched the app
        current_intent = PythonActivity.get = PythonActivity.mActivity.getIntent()
        
        # Check if the Intent has data and is for viewing PDF
        if current_intent is not None:
            action = current_intent.getAction()
            data = current_intent.getData()
            mime_type = current_intent.getType()

            if action == Intent.ACTION_VIEW and data is not None and mime_type == 'application/pdf':
                self.status_label.text = "Receiving PDF..."
                try:
                    # Get InputStream from the URI
                    ContentResolver = autoclass('android.content.ContentResolver')
                    context = PythonActivity.mActivity.getApplicationContext()
                    resolver = context.getContentResolver()
                    
                    input_stream = resolver.openInputStream(data)
                    
                    # Create a temporary file to store the PDF content
                    # Android internal storage (no permission needed)
                    temp_dir = context.getCacheDir().getAbsolutePath() 
                    temp_pdf_path = os.path.join(temp_dir, "temp_shared_pdf.pdf")
                    
                    with open(temp_pdf_path, 'wb') as temp_file:
                        java_buffer_array = autoclass('java.nio.ByteBuffer').allocate(1024 * 4) # 4KB buffer
                        while True:
                            bytes_read = input_stream.read(java_buffer_array.array())
                            if bytes_read == -1: # End of stream
                                break
                            temp_file.write(java_buffer_array.array()[:bytes_read])
                    
                    input_stream.close()
                    self.load_pdf(temp_pdf_path)
                    
                    # Clean up the temporary file after loading (or after app closes)
                    # For now, we leave it for debugging; could delete on app exit
                    
                except Exception as e:
                    self.status_label.text = f"Error processing shared PDF: {e}"
                    print(f"Error processing shared PDF: {e}")
            else:
                self.status_label.text = "App launched, but no PDF shared."
        else:
            self.status_label.text = "App launched, no intent data."


    def load_pdf(self, pdf_path):
        try:
            self.current_pdf_document = fitz.open(pdf_path)
            if len(self.current_pdf_document) == 0:
                self.status_label.text = "Error: PDF contains no pages."
                self.current_pdf_document = None
            else:
                self.current_page_number = 0
                self.total_pages = len(self.current_pdf_document)
                self.display_page(self.current_page_number)
                self.status_label.text = "PDF Loaded."
            self.update_navigation_state()
        except fitz.FileDataError:
            self.status_label.text = "Error: Invalid or corrupted PDF file."
            self.current_pdf_document = None
        except Exception as e:
            self.status_label.text = f"Error loading PDF: {e}"
            self.current_pdf_document = None
        finally:
            # Clean up temp file immediately after loading if desired
            # if os.path.exists(pdf_path) and "temp_shared_pdf.pdf" in pdf_path:
            #     os.remove(pdf_path)
            pass

    @mainthread # Ensure GUI updates happen on the main thread
    def display_page(self, page_num):
        if self.current_pdf_document and 0 <= page_num < self.total_pages:
            page = self.current_pdf_document[page_num]
            mat = fitz.Matrix(self.zoom_factor, self.zoom_factor)
            pix = page.get_pixmap(matrix=mat, alpha=False)

            # PyMuPDF pix.samples is bytes
            # We need to save this to a temporary file for Kivy Image widget
            temp_image_path = os.path.join(App.get_running_app().user_data_dir, "temp_page.png")
            pix.save(temp_image_path)
            
            self.pdf_display_image.source = temp_image_path
            # Reload texture to ensure display updates
            self.pdf_display_image.reload()
            
            self.current_page_number = page_num
            self.page_info_label.text = f"Page: {self.current_page_number + 1}/{self.total_pages}"
        else:
            self.pdf_display_image.source = 'data/logo/kivy-icon-128.png' # Reset to placeholder
            self.pdf_display_image.reload()
            self.page_info_label.text = "Page: 0/0"
            self.status_label.text = "No PDF displayed."

    def show_next_page(self, instance):
        if self.current_pdf_document and self.current_page_number < self.total_pages - 1:
            self.current_page_number += 1
            self.display_page(self.current_page_number)
            self.update_navigation_state()

    def show_previous_page(self, instance):
        if self.current_pdf_document and self.current_page_number > 0:
            self.current_page_number -= 1
            self.display_page(self.current_page_number)
            self.update_navigation_state()

    def update_navigation_state(self):
        if self.current_pdf_document:
            self.prev_button.disabled = (self.current_page_number == 0)
            self.next_button.disabled = (self.current_page_number == self.total_pages - 1)
        else:
            self.prev_button.disabled = True
            self.next_button.disabled = True

    # --- Desktop Testing Helper (NOT part of Android 'no permissions' strategy) ---
    def open_pdf_desktop_test(self, instance):
        if not is_android:
            from plyer import filechooser # Requires plyer library
            try:
                # plyer.filechooser might not work on all desktop setups without backend
                file_path = filechooser.open_file(filters=['*.pdf'], multiselect=False)
                if file_path:
                    self.load_pdf(file_path[0]) # open_file returns a list
                    self.status_label.text = f"Opened: {os.path.basename(file_path[0])}"
            except Exception as e:
                self.status_label.text = f"Desktop file picker error: {e}"
                print(f"Desktop file picker error: {e}")

class PDFReaderApp(App):
    def build(self):
        return PDFReaderLayout()

    def on_start(self):
        # Kivy's user_data_dir is where temporary files can be stored
        # It's an internal directory to the app, no permissions needed.
        self.user_data_dir = self.user_data_dir # Cache for convenience

if __name__ == '__main__':
    PDFReaderApp().run()
