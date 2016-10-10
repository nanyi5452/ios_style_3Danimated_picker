# an ios style picker
an ios-styled picker, with 3d animation. 


how does it look?
--------
![](https://github.com/nanyi5452/ios_style_3Danimated_picker/blob/master/files/general_look.gif)

Material design time pickers look nice, but if you want to have time picked with less area on screen, ios-styled picker seems to me a good choice.



download 
--------
you can either download it and include it as a liberary

or you can add to your build.gradle
```groovy
compile 'com.github.nanyi5452:ios_style_3Danimated_picker:1cdec32d25'
```
and your root build.gradle
```groovy
allprojects {
    repositories {
        jcenter()
        maven { url "https://jitpack.io" }
    }
}
```




usage
--------
use of pick is as simple as below

```xml
    <com.nanyi545.ww.itempicklib.ItemPicker
        android:id="@+id/date_picker"
        android:padding="10dp"
        app:textSize="18sp"
        app:highLightIndicator="round_rect"
        android:background="#FFFFFF"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content" />
```


The items selected are nothing other than an array of integers, the text showing is decided by an implementation of Formatter interface. 
```
    public interface Formatter {
        String format(int item);
    }
```




By calling resetFormatter() you can costumize the items of the picker by changing the integer array, as well as the Formatter instance. 

```java
public void resetFormatter(Formatter formatter)  // changes Formatter instance only.
public void resetFormatter(Formatter formatter,int[] newItemList)  //changes both Formatter and the int array.
public void resetFormatter(Formatter formatter,int[] newItemList,int startIndex)   //   you can specify initial item you want to select.
```



```xml
        app:scrollMode=""    
```
allows you to set if the the items are showing repeated or not

show_once:                                                                            
![](https://github.com/nanyi5452/ios_style_3Danimated_picker/blob/master/files/once.gif)


cyclic:                                                                     
![](https://github.com/nanyi5452/ios_style_3Danimated_picker/blob/master/files/cyclic.gif)




```xml
        app:highLightIndicator=" "
```
allows you to change the drawing to highlight the selected item in the middle. Other alternatives are double_line,single_line,rect,none



