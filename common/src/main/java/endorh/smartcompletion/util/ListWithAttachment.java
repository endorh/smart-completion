package endorh.smartcompletion.util;

import com.google.common.collect.ForwardingList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// Used to pass extra parameters to the CommandsSuggestions.SuggestionsList mixin
public class ListWithAttachment<T, A> extends ForwardingList<T> implements List<T> {
   private List<T> delegate;
   private A attachment;
   public ListWithAttachment(List<T> delegate, A attachment) {
      this.delegate = delegate;
      this.attachment = attachment;
   }
   @Override protected @NotNull List<T> delegate() {
      return delegate;
   }

   public List<T> getDelegate() {
      return delegate;
   }
   public void setDelegate(List<T> delegate) {
      this.delegate = delegate;
   }

   public A getAttachment() {
      return attachment;
   }
   public void setAttachment(A attachment) {
      this.attachment = attachment;
   }

   public static <T, A> ListWithAttachment<T, A> attach(List<T> list, A attachment) {
      return new ListWithAttachment<>(list, attachment);
   }
}
