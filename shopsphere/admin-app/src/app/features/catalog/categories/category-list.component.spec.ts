import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ConfirmService } from '../../../core/ui/confirm-dialog.component';
import { Notifier } from '../../../core/ui/notifier.service';
import { Category } from '../catalog.models';
import { buildCategory } from '../catalog.testing';
import { CategoryApiService } from '../category-api.service';
import { CategoryDialogComponent } from './category-dialog.component';
import { CategoryListComponent } from './category-list.component';

describe('CategoryListComponent', () => {
  let fixture: ComponentFixture<CategoryListComponent>;
  let component: CategoryListComponent;
  let api: jasmine.SpyObj<CategoryApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmService>;
  let notifier: jasmine.SpyObj<Notifier>;

  const categories = [
    buildCategory({ id: 3, name: 'Toys', description: 'Games and puzzles', productCount: 0 }),
    buildCategory({ id: 4, name: 'Books', description: null, productCount: 12 }),
  ];

  function setup(list$: Observable<Category[]> = of(categories)): void {
    api = jasmine.createSpyObj<CategoryApiService>('CategoryApiService', ['list', 'delete']);
    api.list.and.returnValue(list$);
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirm']);
    notifier = jasmine.createSpyObj<Notifier>('Notifier', ['success', 'error']);

    TestBed.configureTestingModule({
      imports: [CategoryListComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: CategoryApiService, useValue: api },
        { provide: MatDialog, useValue: dialog },
        { provide: ConfirmService, useValue: confirm },
        { provide: Notifier, useValue: notifier },
      ],
    });
    fixture = TestBed.createComponent(CategoryListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function dialogClosingWith(result: Category | undefined): MatDialogRef<CategoryDialogComponent, Category> {
    return { afterClosed: () => of(result) } as MatDialogRef<CategoryDialogComponent, Category>;
  }

  function rowTexts(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="category-row"]')).map(
      (row) => (row as HTMLElement).textContent ?? '',
    );
  }

  it('lists categories alphabetically with product counts', () => {
    setup();

    const texts = rowTexts();
    expect(texts.length).toBe(2);
    expect(texts[0]).toContain('Books');
    expect(texts[0]).toContain('12');
    expect(texts[1]).toContain('Toys');
  });

  it('filters client-side by name or description', () => {
    setup();

    component.search.setValue('puzzle');
    fixture.detectChanges();

    expect(rowTexts().length).toBe(1);
    expect(rowTexts()[0]).toContain('Toys');
  });

  it('disables delete for categories that still have products', () => {
    setup();

    const deleteButtons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button[aria-label^="Delete"]'),
    );
    expect(deleteButtons.find((b) => b.getAttribute('aria-label') === 'Delete Books')?.disabled).toBeTrue();
    expect(deleteButtons.find((b) => b.getAttribute('aria-label') === 'Delete Toys')?.disabled).toBeFalse();
  });

  it('creates a category through the dialog and reloads', () => {
    setup();
    dialog.open.and.returnValue(dialogClosingWith(buildCategory({ id: 9, name: 'Garden' })));

    component.openEditor();

    expect(dialog.open).toHaveBeenCalledWith(CategoryDialogComponent, jasmine.objectContaining({ data: { category: undefined } }));
    expect(notifier.success).toHaveBeenCalledWith('Created "Garden".');
    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('passes the category to the dialog when editing, and ignores a cancelled dialog', () => {
    setup();
    dialog.open.and.returnValue(dialogClosingWith(undefined));

    component.openEditor(categories[0]);

    expect(dialog.open).toHaveBeenCalledWith(
      CategoryDialogComponent,
      jasmine.objectContaining({ data: { category: categories[0] } }),
    );
    expect(notifier.success).not.toHaveBeenCalled();
    expect(api.list).toHaveBeenCalledTimes(1);
  });

  it('deletes after confirmation and reloads', () => {
    setup();
    confirm.confirm.and.returnValue(of(true));
    api.delete.and.returnValue(of(undefined));

    component.deleteCategory(categories[0]);

    expect(api.delete).toHaveBeenCalledOnceWith(3);
    expect(notifier.success).toHaveBeenCalledWith('Deleted "Toys".');
    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('explains a category that is still in use (409)', () => {
    setup();
    confirm.confirm.and.returnValue(of(true));
    api.delete.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    component.deleteCategory(categories[0]);

    expect(notifier.error).toHaveBeenCalledWith('"Toys" still has products. Move or delete them first.');
  });

  it('shows an error banner when loading fails', () => {
    setup(throwError(() => new HttpErrorResponse({ status: 0 })));

    const banner: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="list-error"]');
    expect(banner?.textContent).toContain('Cannot reach the server');
  });
});
